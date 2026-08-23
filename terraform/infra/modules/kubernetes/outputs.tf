output "cluster_id" {
  value       = yandex_kubernetes_cluster.this.id
  description = "ID созданного кластера Kubernetes"
}

# external_v4_endpoint приходит от API без порта — реальный API-сервер слушает на 6443, не 443.
output "cluster_endpoint" {
  value = "${yandex_kubernetes_cluster.this.master[0].external_v4_endpoint}:6443"
}

output "cluster_ca_certificate" {
  value     = yandex_kubernetes_cluster.this.master[0].cluster_ca_certificate
  sensitive = true
}

output "node_service_account_id" {
  value = yandex_iam_service_account.node.id
}

output "cluster_status" {
  value       = yandex_kubernetes_cluster.this.status
  description = "Текущий статус кластера (ожидается RUNNING)"
}
