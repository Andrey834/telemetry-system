variable "cluster_name" {
  type = string
}

variable "zone" {
  type = string
}

variable "subnet_cidr" {
  type    = string
  default = "10.10.0.0/24"
}

variable "folder_id" {
  type = string
}
