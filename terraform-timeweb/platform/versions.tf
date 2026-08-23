terraform {
  required_version = ">= 0.13"

  required_providers {
    twc = {
      source = "timeweb-cloud/timeweb-cloud"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.33"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.16"
    }
  }
}
