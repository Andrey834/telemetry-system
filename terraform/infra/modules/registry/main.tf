resource "yandex_container_registry" "this" {
  name      = "${var.cluster_name}-registry"
  folder_id = var.folder_id
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
