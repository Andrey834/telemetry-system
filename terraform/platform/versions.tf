terraform {
  required_version = ">= 1.7.0"

  required_providers {
    yandex = {
      source  = "yandex-cloud/yandex"
      version = "~> 0.130"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.33"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.16"
    }
    # kubectl (не kubernetes_manifest) — умеет создавать CR ArgoCD Application в том же apply,
    # где сама ArgoCD (и её CRD) только что установлена; kubernetes_manifest требует, чтобы CRD
    # уже существовал на этапе plan, что не работает при чистом apply с нуля.
    kubectl = {
      source  = "gavinbunney/kubectl"
      version = "~> 1.14"
    }
  }
}
