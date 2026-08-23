output "cluster_id" {
  value = module.kubernetes.cluster_id
}

output "cluster_name" {
  description = "Нужно стадии platform/ — data \"yandex_kubernetes_cluster\" ищет кластер по имени"
  value       = var.cluster_name
}

output "cluster_endpoint" {
  value = module.kubernetes.cluster_endpoint
}

output "ingress_static_ip" {
  description = "Зарезервированный публичный IP — используйте в LoadBalancer Service/Ingress для ingestion-service"
  value       = module.network.ingress_static_ip
}

output "registry_id" {
  value = module.registry.registry_id
}

output "kubeconfig_command" {
  description = "Выполните после terraform apply, чтобы получить доступ к кластеру через kubectl/helm"
  value       = "yc managed-kubernetes cluster get-credentials ${var.cluster_name} --external --force"
}
