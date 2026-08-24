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
  type    = string
  default = "telemetry-system"
}

variable "node_count" {
  description = "Число рабочих нод в node group"
  type        = number
  default     = 3
}

variable "node_cores" {
  type    = number
  default = 2
}

variable "node_memory" {
  description = "Память на ноду, ГБ"
  type        = number
  default     = 4
}

variable "preemptible" {
  description = "Прерываемые (дешёвые, но Yandex может остановить их в течение 24ч) ноды — годится для теста/демо, не для прод-нагрузки"
  type        = bool
  default     = false
}

variable "postgres_password" {
  type      = string
  sensitive = true
}

variable "redis_password" {
  type      = string
  sensitive = true
}

variable "kafka_password" {
  type      = string
  sensitive = true
}
