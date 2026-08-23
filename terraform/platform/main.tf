# Кластер создаётся отдельным конфигом (../infra) и здесь только читается через data source —
# намеренное разделение на два state, чтобы не создавать кластер и не разворачивать в него
# ресурсы через kubernetes/helm-провайдеры в одном apply.
data "yandex_kubernetes_cluster" "this" {
  name = var.cluster_name
}

module "platform" {
  source = "./modules/platform"

  cluster_id              = data.yandex_kubernetes_cluster.this.id
  grafana_admin_password  = var.grafana_admin_password
  folder_id               = var.yc_folder_id
  prometheus_workspace_id = var.prometheus_workspace_id
  enable_cert_manager     = var.enable_cert_manager
}
