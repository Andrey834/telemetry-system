#!/usr/bin/env bash
# Пересобирает и пушит изменившиеся образы сервисов в Yandex Container Registry и перезапускает
# деплойменты. Секреты с паролями БД/Kafka теперь полностью управляются Terraform'ом
# (terraform/platform/argocd_apps.tf) — этот скрипт их больше не трогает.
#
# Запуск из корня репозитория: ./scripts/deploy-yandex.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NAMESPACE="telemetry-system"

REGISTRY_ID="$(cd "$ROOT_DIR/terraform/infra" && terraform output -raw registry_id)"
REGISTRY="cr.yandex/$REGISTRY_ID"
SERVICES=(ingestion-service telemetry-processor query-service dashboard)

# ~/.kube/config общий для Timeweb и Yandex-кластеров — явно переключаемся, чтобы случайно не
# накатить рестарты не на тот кластер.
KUBE_CONTEXT="${YC_KUBE_CONTEXT:-yc-telemetry-system}"
echo "==> Переключаю kubectl-контекст на $KUBE_CONTEXT"
kubectl config use-context "$KUBE_CONTEXT"

echo "==> Логин в $REGISTRY (через yc CLI, без ручного токена)"
# docker logout сбрасывает закешированные credential'ы cr.yandex — без этого после пересоздания
# registry (terraform destroy/apply с новым registry_id) push иногда падает с "Registry <старый
# id> not found", хотя configure-docker формально проходит успешно.
docker logout cr.yandex >/dev/null 2>&1 || true
yc container registry configure-docker

for svc in "${SERVICES[@]}"; do
  echo "==> Сборка и пуш $svc"
  docker build --platform linux/amd64 -t "$REGISTRY/$svc:0.0.1" "$ROOT_DIR/$svc"
  docker push "$REGISTRY/$svc:0.0.1"

  echo "==> Рестарт deployment/$svc-$svc (тег образа не меняется — IfNotPresent/Always сам не всегда перечитает вовремя)"
  kubectl -n "$NAMESPACE" rollout restart "deployment/$svc-$svc" || true
done

echo "==> Готово. Проверьте: kubectl -n $NAMESPACE get pods"
