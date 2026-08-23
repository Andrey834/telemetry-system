terraform {
  required_version = ">= 0.13"

  required_providers {
    twc = {
      source = "timeweb-cloud/timeweb-cloud"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
  }
}
