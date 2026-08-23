variable "cluster_id" {
  type = string
}

variable "prometheus_workspace_id" {
  description = "ID workspace Yandex Managed Service for Prometheus (создаётся вручную, Terraform-ресурса для этого нет)"
  type        = string
}

variable "folder_id" {
  type = string
}

variable "grafana_admin_password" {
  description = "Пароль admin для Grafana — задайте через terraform.tfvars, не оставляйте дефолт в проде"
  type        = string
  sensitive   = true
  default     = "change-me"
}

variable "ingress_nginx_version" {
  type    = string
  default = "4.11.3"
}

variable "cert_manager_version" {
  type    = string
  default = "v1.16.2"
}

variable "enable_cert_manager" {
  type    = bool
  default = false
}
