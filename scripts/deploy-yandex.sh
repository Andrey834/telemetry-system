#!/usr/bin/env bash
# Пересобирает и пушит изменившиеся образы сервисов в Yandex Container Registry, создаёт/обновляет
# secret'ы с паролями БД в кластере. Пароли берутся из terraform/infra/terraform.tfvars
# (локальный, не в git) — вводить их руками не нужно.
#
# Запуск из корня репозитория: ./scripts/deploy-yandex.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TFVARS="$ROOT_DIR/terraform/infra/terraform.tfvars"
NAMESPACE="telemetry-system"

if [[ ! -f "$TFVARS" ]]; then
  echo "Не найден $TFVARS — сначала выполните terraform apply в terraform/infra/" >&2
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

REGISTRY_ID="$(cd "$ROOT_DIR/terraform/infra" && terraform output -raw registry_id)"
REGISTRY="cr.yandex/$REGISTRY_ID"
SERVICES=(ingestion-service telemetry-processor query-service dashboard)

# ~/.kube/config общий для Timeweb и Yandex-кластеров — явно переключаемся, чтобы случайно не
# накатить секреты/рестарты не на тот кластер.
KUBE_CONTEXT="${YC_KUBE_CONTEXT:-yc-telemetry-system}"
echo "==> Переключаю kubectl-контекст на $KUBE_CONTEXT"
kubectl config use-context "$KUBE_CONTEXT"

echo "==> Секреты в кластере ($NAMESPACE)"

kubectl -n "$NAMESPACE" create secret generic ingestion-service-credentials \
  --from-literal=KAFKA_PASSWORD="$KAFKA_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n "$NAMESPACE" create secret generic telemetry-processor-credentials \
  --from-literal=DB_PASSWORD="$POSTGRES_PASSWORD" \
  --from-literal=REDIS_PASSWORD="$REDIS_PASSWORD" \
  --from-literal=KAFKA_PASSWORD="$KAFKA_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n "$NAMESPACE" create secret generic query-service-credentials \
  --from-literal=REDIS_PASSWORD="$REDIS_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "==> Логин в $REGISTRY (через yc CLI, без ручного токена)"
yc container registry configure-docker

for svc in "${SERVICES[@]}"; do
  echo "==> Сборка и пуш $svc"
  docker build --platform linux/amd64 -t "$REGISTRY/$svc:0.0.1" "$ROOT_DIR/$svc"
  docker push "$REGISTRY/$svc:0.0.1"

  echo "==> Рестарт deployment/$svc-$svc (тег образа не меняется — IfNotPresent/Always сам не всегда перечитает вовремя)"
  kubectl -n "$NAMESPACE" rollout restart "deployment/$svc-$svc" || true
done

echo "==> Готово. Проверьте: kubectl -n $NAMESPACE get pods"
