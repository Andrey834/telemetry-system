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

# Тот же продукт маркетплейса "Prometheus Operator с поддержкой Yandex Monitoring" (тот же чарт,
# те же образы на cr.yandex), но ставим напрямую по OCI, а не через
# yandex_kubernetes_marketplace_helm_release — у маркетплейс-ресурса user_values ограничен
# узкой курируемой схемой (только prometheusWorkspaceId/iam_api_key_value_generated), которая
# отклоняет grafana.adminPassword/sidecar.* с ошибкой "value ... not found", хотя эти ключи
# реально есть в values.yaml чарта (проверено — скачали чарт напрямую из cr.yandex). Обычный
# helm_release такого ограничения не имеет.
resource "helm_release" "kube_prometheus_stack" {
  name       = "kube-prometheus-stack"
  repository = "oci://cr.yandex/yc-marketplace/yandex-cloud/prometheus/charts"
  chart      = "kube-prometheus-stack"
  version    = "86.2.3-1"
  namespace  = kubernetes_namespace.monitoring.metadata[0].name

  values = [
    yamlencode({
      prometheusWorkspaceId = var.prometheus_workspace_id
      iam_api_key_value_generated = {
        secretAccessKey = yandex_iam_service_account_api_key.prometheus_remote_write_key.secret_key
      }
      grafana = {
        adminPassword = var.grafana_admin_password
        service = {
          type = "ClusterIP" # наружу — через Ingress/port-forward, не публичный LoadBalancer напрямую
        }
        sidecar = {
          dashboards = {
            enabled         = true
            label           = "grafana_dashboard"
            labelValue      = "1"
            searchNamespace = "ALL" # дашборд едет в неймспейсе приложения, не monitoring — без ALL sidecar видел бы ConfigMap только в своём namespace
          }
        }
      }
      prometheus = {
        prometheusSpec = {
          retention                               = "15d"
          serviceMonitorSelectorNilUsesHelmValues = false # подхватывать ServiceMonitor из любого namespace, не только своего релиза
        }
      }
    })
  ]
}

# Тот же продукт маркетплейса "Argo CD" (тот же чарт, образ уже смотрит на cr.yandex), но ставим
# напрямую по OCI, а не через yandex_kubernetes_marketplace_helm_release — та же причина, что и
# для kube-prometheus-stack: курируемая схема user_values отклоняла configs.params.server.insecure.
# "server.insecure" — буквальный ключ с точкой внутри configs.params (не вложенность), проверено
# по values.yaml чарта напрямую.
resource "helm_release" "argocd" {
  name       = "argocd"
  repository = "oci://cr.yandex/yc-marketplace/yandex-cloud/argo/chart"
  chart      = "argo-cd"
  version    = "7.3.11-2"
  namespace  = kubernetes_namespace.argocd.metadata[0].name

  values = [
    yamlencode({
      configs = {
        params = {
          "server.insecure" = true # TLS терминируется на Ingress/LoadBalancer перед ArgoCD — для демо-стенда достаточно
        }
      }
    })
  ]
}
