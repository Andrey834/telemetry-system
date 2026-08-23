provider "twc" {}

# kubeconfig создан стадией ../infra (local_sensitive_file) — читаем готовый файл, не пересобираем.
provider "kubernetes" {
  config_path = "${path.module}/../kubeconfig.yaml"
}

provider "helm" {
  kubernetes {
    config_path = "${path.module}/../kubeconfig.yaml"
  }
}
