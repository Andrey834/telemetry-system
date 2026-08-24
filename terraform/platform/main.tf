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

# ClusterIssuer живёт здесь, а не в модуле platform — CRD cert-manager.io/v1 появляется только
# после helm_release.cert_manager, а kubectl_manifest (в отличие от kubernetes_manifest) не
# требует существования CRD на этапе plan, так что порядок применения решается через depends_on.
resource "kubectl_manifest" "letsencrypt_staging" {
  count = var.enable_cert_manager ? 1 : 0

  yaml_body = yamlencode({
    apiVersion = "cert-manager.io/v1"
    kind       = "ClusterIssuer"
    metadata   = { name = "letsencrypt-staging" }
    spec = {
      acme = {
        server              = "https://acme-staging-v02.api.letsencrypt.org/directory"
        email               = var.letsencrypt_email
        privateKeySecretRef = { name = "letsencrypt-staging-key" }
        solvers = [
          { http01 = { ingress = { ingressClassName = "nginx" } } }
        ]
      }
    }
  })

  depends_on = [module.platform]
}

resource "kubectl_manifest" "letsencrypt_prod" {
  count = var.enable_cert_manager ? 1 : 0

  yaml_body = yamlencode({
    apiVersion = "cert-manager.io/v1"
    kind       = "ClusterIssuer"
    metadata   = { name = "letsencrypt-prod" }
    spec = {
      acme = {
        server              = "https://acme-v02.api.letsencrypt.org/directory"
        email               = var.letsencrypt_email
        privateKeySecretRef = { name = "letsencrypt-prod-key" }
        solvers = [
          { http01 = { ingress = { ingressClassName = "nginx" } } }
        ]
      }
    }
  })

  depends_on = [module.platform]
}
