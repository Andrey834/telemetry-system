#!/usr/bin/env bash
# Собирает и пушит образы сервисов в Yandex Container Registry с тегом = короткий git-хэш
# коммита (не постоянно перезаписываемый "0.0.1" — так у каждого деплоя реальная версия,
# на которую можно откатиться). Сам деплой (обновление тега в ArgoCD Application) — отдельным
# шагом через terraform apply -var image_tag=..., команда печатается в конце.
#
# Запуск из корня репозитория: ./scripts/deploy-yandex.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

REGISTRY_ID="$(cd "$ROOT_DIR/terraform/infra" && terraform output -raw registry_id)"
REGISTRY="cr.yandex/$REGISTRY_ID"
SERVICES=(ingestion-service telemetry-processor query-service dashboard)

IMAGE_TAG="$(cd "$ROOT_DIR" && git rev-parse --short HEAD)"
if [[ -n "$(cd "$ROOT_DIR" && git status --porcelain)" ]]; then
  IMAGE_TAG="${IMAGE_TAG}-dirty"
  echo "==> В рабочей копии есть незакоммиченные изменения — тег образа: $IMAGE_TAG" >&2
fi
echo "==> Тег образов: $IMAGE_TAG"

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
  echo "==> Сборка и пуш $svc:$IMAGE_TAG"
  docker build --platform linux/amd64 -t "$REGISTRY/$svc:$IMAGE_TAG" "$ROOT_DIR/$svc"
  push_with_retry "$REGISTRY/$svc:$IMAGE_TAG" "$svc"
done

echo "==> Образы запушены. Выполните, чтобы задеплоить именно эту версию:"
echo
echo "    cd terraform/platform && terraform apply -var image_tag=$IMAGE_TAG"
echo
echo "ArgoCD сама применит новый тег и перекатит поды — kubectl rollout restart больше не нужен."
