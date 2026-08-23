variable "cluster_name" {
  type = string
}

variable "folder_id" {
  type = string
}

variable "zone" {
  type = string
}

variable "network_id" {
  type = string
}

variable "subnet_id" {
  type = string
}

variable "security_group_id" {
  type = string
}

variable "default_security_group_id" {
  type = string
}

variable "k8s_version" {
  type    = string
  default = "1.35"
}

variable "node_count" {
  type    = number
  default = 3
}

variable "node_cores" {
  type    = number
  default = 4
}

variable "node_memory" {
  type    = number
  default = 8
}

variable "preemptible" {
  description = "Прерываемые (дешёвые, но могут быть остановлены Yandex) ноды — годится для демо, не для прод-нагрузки"
  type        = bool
  default     = false
}

variable "registry_id" {
  type    = string
  default = null
}