resource "kubernetes_namespace" "monitoring" {
  metadata {
    name = "monitoring"
  }
}

resource "kubernetes_namespace" "argocd" {
  metadata {
    name = "argocd"
  }
}

resource "kubernetes_namespace" "ingress_nginx" {
  metadata {
    name = "ingress-nginx"
  }
}

# Выключен по умолчанию — без реального домена Let's Encrypt не выпустит сертификат.
resource "kubernetes_namespace" "cert_manager" {
  count = var.enable_cert_manager ? 1 : 0
  metadata {
    name = "cert-manager"
  }
}

# Один и тот же ключ используется чартом и для remote_write, и для remote_read в managed workspace.
resource "yandex_iam_service_account" "prometheus_remote_write" {
  name      = "prometheus-remote-write"
  folder_id = var.folder_id
}

resource "yandex_resourcemanager_folder_iam_member" "prometheus_remote_write_role" {
  folder_id = var.folder_id
  role      = "monitoring.editor"
  member    = "serviceAccount:${yandex_iam_service_account.prometheus_remote_write.id}"
}

resource "yandex_iam_service_account_api_key" "prometheus_remote_write_key" {
  service_account_id = yandex_iam_service_account.prometheus_remote_write.id
  description        = "remote_write/remote_read в Managed Service for Prometheus из kube-prometheus-stack"
}

resource "helm_release" "ingress_nginx" {
  name       = "ingress-nginx"
  repository = "https://kubernetes.github.io/ingress-nginx"
  chart      = "ingress-nginx"
  version    = var.ingress_nginx_version
  namespace  = kubernetes_namespace.ingress_nginx.metadata[0].name

  values = [
    yamlencode({
      controller = {
        service = {
          type = "LoadBalancer"
        }
      }
    })
  ]
}

# Образы cert-manager идут с quay.io, недоступного с нод Yandex Cloud — готового зеркала на
# cr.yandex для него нет ни в одном маркетплейс-продукте. Разбираться с этим стоит, когда
# появится домен и cert-manager реально понадобится включать.
resource "helm_release" "cert_manager" {
  count      = var.enable_cert_manager ? 1 : 0
  name       = "cert-manager"
  repository = "https://charts.jetstack.io"
  chart      = "cert-manager"
  version    = var.cert_manager_version
  namespace  = kubernetes_namespace.cert_manager[0].metadata[0].name

  set {
    name  = "installCRDs"
    value = "true"
  }
}

# Продукт маркетплейса "Prometheus Operator с поддержкой Yandex Monitoring" — его образы уже
# смотрят на cr.yandex, а не на недоступный с нод quay.io. user_values — плоские dot-notation
# ключи (как helm --set), не вложенный YAML.
resource "yandex_kubernetes_marketplace_helm_release" "kube_prometheus_stack" {
  cluster_id = var.cluster_id

  product_version = "f2e4808kdtr6v9l5fmvs" # Prometheus Operator с поддержкой Yandex Monitoring, версия чарта 86.2.3-1

  name      = "kube-prometheus-stack"
  namespace = kubernetes_namespace.monitoring.metadata[0].name

  user_values = {
    "prometheusWorkspaceId"                                             = var.prometheus_workspace_id
    "iam_api_key_value_generated.secretAccessKey"                       = yandex_iam_service_account_api_key.prometheus_remote_write_key.secret_key
    "grafana.adminPassword"                                             = var.grafana_admin_password
    "grafana.service.type" = "ClusterIP" # наружу — через Ingress/port-forward, не публичный LoadBalancer напрямую
    # Весь блок grafana.sidecar.dashboards.* убран — текущая версия marketplace-продукта не
    # принимает эти ключи ("value ... not found" на каждом по очереди), схема changed с момента
    # настройки. Автоподхват дашбордов из ConfigMap по лейблу grafana_dashboard сейчас не
    # настроен — нужно сверить актуальную схему values чарта отдельно, прежде чем включать обратно.
    "prometheus.prometheusSpec.retention"                               = "15d"
    "prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues" = "false" # подхватывать ServiceMonitor из любого namespace, не только своего релиза
  }
}

# Продукт маркетплейса "Argo CD" — версия чарта отстаёт от апстрима argoproj/argo-helm, но образ
# смотрит на cr.yandex, а не на недоступный quay.io.
resource "yandex_kubernetes_marketplace_helm_release" "argocd" {
  cluster_id = var.cluster_id

  product_version = "f2et3m5qhh5av80s6qbl" # Argo CD, версия чарта 7.3.11-2

  name      = "argocd"
  namespace = kubernetes_namespace.argocd.metadata[0].name

  # user_values пуст: configs.params.server.insecure текущая версия marketplace-продукта не
  # принимает ("value ... not found") — схема изменилась с момента настройки. ArgoCD ставится
  # с дефолтами продукта; доступ через port-forward будет по https, не http.
  user_values = {}
}
