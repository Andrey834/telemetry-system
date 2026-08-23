variable "cluster_name" {
  type    = string
  default = "telemetry-system"
}

variable "location" {
  description = "ru-1 — SPB (spb-3), ru-3 — MSK (msk-1), nl-1 — AMS (ams-1). Master и worker должны быть в одной локации"
  type        = string
  default     = "ru-1"
}

variable "vpc_subnet_cidr" {
  type    = string
  default = "192.168.0.0/24"
}

variable "k8s_version" {
  # Формат версии включает суффикс дистрибутива k0s, например "v1.34.10+k0s.0" — без него
  # версия не находится. Актуальный список: curl .../api/v1/k8s/k8s_versions -H "Authorization: Bearer $TWC_TOKEN"
  description = "Полная версия с суффиксом k0s, см. https://api.timeweb.cloud/api/v1/k8s/k8s_versions"
  type        = string
  default     = "v1.34.10+k0s.0"
}

variable "network_driver" {
  type    = string
  default = "flannel"
}

variable "high_availability" {
  description = "Несколько master-узлов вместо одного — для демо/портфолио избыточно, дороже"
  type        = bool
  default     = false
}

variable "master_cpu" {
  description = "Фильтр CPU для подбора пресета master-узла через data.twc_k8s_preset"
  type        = number
  default     = 2
}

variable "worker_cpu" {
  description = "Фильтр CPU для подбора пресета worker-узлов через data.twc_k8s_preset"
  type        = number
  default     = 2
}

variable "worker_count" {
  type    = number
  default = 3
}
