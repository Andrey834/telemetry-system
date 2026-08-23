# config_type=custom/yaml_config не проверялся (формат yaml_config для каждого addon не
# задокументирован построчно) — используем basic (дефолты Timeweb). Пароль admin для Grafana
# в этом режиме генерирует сам addon: kubectl get secret в namespace addon'а.
resource "twc_k8s_addon" "kube_prometheus_stack" {
  cluster_id  = var.cluster_id
  type        = "kube-prometheus-stack"
  config_type = "basic"
  version     = "66.2.1" # версия из карточки addon'а в консоли Timeweb — API требует валидный semver
}

resource "twc_k8s_addon" "cert_manager" {
  count = var.enable_cert_manager ? 1 : 0

  cluster_id  = var.cluster_id
  type        = "cert-manager"
  config_type = "basic"
  version     = "1.16.1" # версия из карточки addon'а в консоли Timeweb
}

# В консоли Timeweb ArgoCD показан как addon, но в провайдере такого типа нет — ставим обычным
# helm_release из апстрима, версией чарта под ту же 8.0.0, что показывает консоль.
resource "kubernetes_namespace" "argocd" {
  metadata {
    name = "argocd"
  }
}

resource "helm_release" "argocd" {
  name       = "argocd"
  repository = "https://argoproj.github.io/argo-helm"
  chart      = "argo-cd"
  version    = var.argocd_version
  namespace  = kubernetes_namespace.argocd.metadata[0].name

  set {
    name  = "configs.params.server.insecure"
    value = "true" # TLS терминируется на ingress перед ArgoCD — для демо-стенда достаточно
  }

  dynamic "set_sensitive" {
    for_each = var.argocd_admin_password_bcrypt_hash != null ? [var.argocd_admin_password_bcrypt_hash] : []
    content {
      name  = "configs.secret.argocdServerAdminPassword"
      value = set_sensitive.value
    }
  }
}
