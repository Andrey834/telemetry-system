#!/usr/bin/env bash
# Пересобирает и пушит изменившиеся образы сервисов в Timeweb Container Registry, создаёт/обновляет
# secret'ы с паролями БД в кластере. Пароли берутся из terraform-timeweb/infra/terraform.tfvars
# (локальный, не в git) — вводить их руками не нужно.
#
# Запуск из корня репозитория: ./scripts/deploy-timeweb.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TFVARS="$ROOT_DIR/terraform-timeweb/infra/terraform.tfvars"
KUBECONFIG_PATH="$ROOT_DIR/terraform-timeweb/kubeconfig.yaml"
REGISTRY="3829bdb3-inventive-amalthea.registry.twcstorage.ru"
NAMESPACE="telemetry-system"

# Сервисы, чей код/ресурсы меняются чаще всего — при необходимости добавьте свой сюда.
SERVICES=(ingestion-service telemetry-processor)

if [[ ! -f "$TFVARS" ]]; then
  echo "Не найден $TFVARS — сначала выполните terraform apply в terraform-timeweb/infra/" >&2
  exit 1
fi

tfvar() {
  grep -E "^$1[[:space:]]*=" "$TFVARS" | sed -E 's/^[^=]+=[[:space:]]*"([^"]*)".*/\1/'
}

POSTGRES_PASSWORD="$(tfvar postgres_password)"
REDIS_PASSWORD="$(tfvar redis_password)"
KAFKA_PASSWORD="$(tfvar kafka_password)"

if [[ -z "$POSTGRES_PASSWORD" || -z "$REDIS_PASSWORD" || -z "$KAFKA_PASSWORD" ]]; then
  echo "Не удалось прочитать пароли из $TFVARS" >&2
  exit 1
fi

echo "==> Секреты в кластере ($NAMESPACE)"

kubectl --kubeconfig "$KUBECONFIG_PATH" -n "$NAMESPACE" create secret generic ingestion-service-credentials \
  --from-literal=KAFKA_PASSWORD="$KAFKA_PASSWORD" \
  --dry-run=client -o yaml | kubectl --kubeconfig "$KUBECONFIG_PATH" apply -f -

kubectl --kubeconfig "$KUBECONFIG_PATH" -n "$NAMESPACE" create secret generic telemetry-processor-credentials \
  --from-literal=DB_PASSWORD="$POSTGRES_PASSWORD" \
  --from-literal=REDIS_PASSWORD="$REDIS_PASSWORD" \
  --from-literal=KAFKA_PASSWORD="$KAFKA_PASSWORD" \
  --dry-run=client -o yaml | kubectl --kubeconfig "$KUBECONFIG_PATH" apply -f -

kubectl --kubeconfig "$KUBECONFIG_PATH" -n "$NAMESPACE" create secret generic query-service-credentials \
  --from-literal=REDIS_PASSWORD="$REDIS_PASSWORD" \
  --dry-run=client -o yaml | kubectl --kubeconfig "$KUBECONFIG_PATH" apply -f -

echo "==> Логин в $REGISTRY"
if [[ -z "${TWC_TOKEN:-}" ]]; then
  echo "TWC_TOKEN не задан — export TWC_TOKEN=<токен с https://timeweb.cloud/my/api-keys>" >&2
  exit 1
fi
echo "$TWC_TOKEN" | docker login "$REGISTRY" -u 3829bdb3-inventive-amalthea --password-stdin

for svc in "${SERVICES[@]}"; do
  echo "==> Сборка и пуш $svc"
  docker build --platform linux/amd64 -t "$REGISTRY/$svc:0.0.1" "$ROOT_DIR/$svc"
  docker push "$REGISTRY/$svc:0.0.1"

  echo "==> Рестарт deployment/$svc-$svc (тег образа не меняется — IfNotPresent сам не перечитает)"
  kubectl --kubeconfig "$KUBECONFIG_PATH" -n "$NAMESPACE" rollout restart "deployment/$svc-$svc" || true
done

echo "==> Готово. Проверьте: kubectl --kubeconfig $KUBECONFIG_PATH -n $NAMESPACE get pods"
