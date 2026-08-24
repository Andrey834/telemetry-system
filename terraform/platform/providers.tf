provider "yandex" {
  token     = var.yc_token
  cloud_id  = var.yc_cloud_id
  folder_id = var.yc_folder_id
  zone      = var.default_zone
}

# external_v4_endpoint без порта указывает не туда, где реально слушает API-сервер — это 6443, не 443.
provider "kubernetes" {
  host                   = "${data.yandex_kubernetes_cluster.this.master[0].external_v4_endpoint}:6443"
  cluster_ca_certificate = data.yandex_kubernetes_cluster.this.master[0].cluster_ca_certificate
  token                  = var.yc_token
}

provider "helm" {
  kubernetes {
    host                   = "${data.yandex_kubernetes_cluster.this.master[0].external_v4_endpoint}:6443"
    cluster_ca_certificate = data.yandex_kubernetes_cluster.this.master[0].cluster_ca_certificate
    token                  = var.yc_token
  }
}

provider "kubectl" {
  host                   = "${data.yandex_kubernetes_cluster.this.master[0].external_v4_endpoint}:6443"
  cluster_ca_certificate = data.yandex_kubernetes_cluster.this.master[0].cluster_ca_certificate
  token                  = var.yc_token
  load_config_file       = false
}
