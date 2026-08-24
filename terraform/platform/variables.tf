variable "yc_token" {
  description = "IAM-токен Yandex Cloud — export TF_VAR_yc_token=$(yc iam create-token)"
  type        = string
  sensitive   = true
  default     = null
}

variable "yc_cloud_id" {
  type = string
}

variable "yc_folder_id" {
  type = string
}

variable "default_zone" {
  type    = string
  default = "ru-central1-a"
}

variable "cluster_name" {
  description = "Должно совпадать с var.cluster_name в ../infra"
  type        = string
  default     = "telemetry-system"
}

variable "grafana_admin_password" {
  description = "Пароль admin для Grafana"
  type        = string
  sensitive   = true
  default     = "change-me"
}

variable "prometheus_workspace_id" {
  description = "ID workspace Yandex Managed Service for Prometheus — создаётся вручную (yc monitoring prometheus workspace create), Terraform-ресурса для него нет"
  type        = string
}

variable "enable_cert_manager" {
  type    = bool
  default = false
}

variable "letsencrypt_email" {
  description = "Email для уведомлений Let's Encrypt об истечении сертификата (ClusterIssuer)"
  type        = string
  default     = null
}

variable "dashboard_basic_auth_user" {
  description = "Логин Basic Auth для публичного dashboard"
  type        = string
  default     = null
}

variable "dashboard_basic_auth_password" {
  description = "Пароль Basic Auth для публичного dashboard"
  type        = string
  sensitive   = true
  default     = null
}

variable "dashboard_host" {
  description = "Домен dashboard (Angular UI) — задаётся в terraform.tfvars, не коммитится"
  type        = string
  default     = null
}

variable "query_service_host" {
  description = "Домен query-service (публичный API) — задаётся в terraform.tfvars, не коммитится"
  type        = string
  default     = null
}

variable "ingestion_service_host" {
  description = "Домен ingestion-service (публичный приём телеметрии) — задаётся в terraform.tfvars, не коммитится"
  type        = string
  default     = null
}

variable "dns_zone" {
  description = "Базовая DNS-зона на reg.ru (например example.com) — dashboard_host/query_service_host/ingestion_service_host должны быть её поддоменами"
  type        = string
  default     = null
}

variable "regru_username" {
  description = "Логин (email) аккаунта reg.ru для REG.API v2"
  type        = string
  default     = null
}

variable "regru_password" {
  description = "Пароль аккаунта reg.ru для REG.API v2"
  type        = string
  sensitive   = true
  default     = null
}

variable "regru_cert_path" {
  description = "Путь к публичному SSL-сертификату клиента REG.API (загружен в настройках безопасности reg.ru)"
  type        = string
  default     = null
}

variable "regru_key_path" {
  description = "Путь к приватному ключу SSL-сертификата клиента REG.API"
  type        = string
  sensitive   = true
  default     = null
}
