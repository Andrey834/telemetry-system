resource "yandex_container_registry" "this" {
  name      = "${var.cluster_name}-registry"
  folder_id = var.folder_id
}

# Yandex не даёт удалить registry, если в нём остались образы ("Registry ... is not empty, you
# must delete all images first") — terraform destroy падает на этом шаге. Перед удалением самого
# registry сначала чистим все образы через yc CLI (требует локально настроенный yc с доступом
# к тому же folder).
resource "null_resource" "empty_registry_before_destroy" {
  triggers = {
    registry_id = yandex_container_registry.this.id
  }

  provisioner "local-exec" {
    when    = destroy
    command = <<-EOT
      set -e
      ids=$(yc container image list --registry-id "${self.triggers.registry_id}" --format json | jq -r '.[].id')
      for id in $ids; do
        yc container image delete "$id" || true
      done
    EOT
  }
}

# Сервисный аккаунт, от имени которого ноды кластера будут забирать образы из реестра.
resource "yandex_iam_service_account" "puller" {
  name      = "${var.cluster_name}-registry-puller"
  folder_id = var.folder_id
}

resource "yandex_resourcemanager_folder_iam_member" "puller_role" {
  folder_id = var.folder_id
  role      = "container-registry.images.puller"
  member    = "serviceAccount:${yandex_iam_service_account.puller.id}"
}
