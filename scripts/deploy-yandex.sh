#!/usr/bin/env bash
# Пересобирает и пушит изменившиеся образы сервисов в Yandex Container Registry и перезапускает
# деплойменты.
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
docker logout cr.yandex >/dev/null 2>&1 || true
yc container registry configure-docker

# После destroy/apply реестра с новым registry_id Docker иногда пытается cross-repository blob
# mount на слой, который помнит по хешу содержимого с УЖЕ УДАЛЁННОГО старого registry — push
# падает с "Registry <старый id> not found". Чинится только полной очисток локального
# image/content store (docker buildx prune недостаточно) — делаем это один раз за прогон, только
# если реально словили эту ошибку, а не на каждый запуск (иначе стирали бы кэш вообще всех
# остальных локальных проектов зря).
pruned_once=0

push_with_retry() {
  local image="$1"
  local svc="$2"
  local output
  if output="$(docker push "$image" 2>&1)"; then
    echo "$output"
    return 0
  fi
  echo "$output"

  if [[ "$pruned_once" -eq 1 ]] || ! grep -q "not found" <<<"$output"; then
    return 1
  fi

  echo "==> Похоже на баг с закэшированным cross-repo mount на удалённый registry — чищу Docker (docker system prune -af) и пересобираю $svc"
  docker system prune -af
  pruned_once=1
  docker build --platform linux/amd64 -t "$image" "$ROOT_DIR/$svc"
  docker push "$image"
}

for svc in "${SERVICES[@]}"; do
  echo "==> Сборка и пуш $svc"
  docker build --platform linux/amd64 -t "$REGISTRY/$svc:0.0.1" "$ROOT_DIR/$svc"
  push_with_retry "$REGISTRY/$svc:0.0.1" "$svc"

  echo "==> Рестарт deployment/$svc-$svc (тег образа не меняется — IfNotPresent/Always сам не всегда перечитает вовремя)"
  kubectl -n "$NAMESPACE" rollout restart "deployment/$svc-$svc" || true
done

echo "==> Готово. Проверьте: kubectl -n $NAMESPACE get pods"
