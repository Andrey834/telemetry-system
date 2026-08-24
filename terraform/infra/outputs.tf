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

output "postgres_host" {
  value = yandex_mdb_postgresql_cluster.this.host[0].fqdn
}

output "postgres_port" {
  value = 6432 # порт Yandex Managed PostgreSQL всегда 6432 (через встроенный connection pooler)
}

output "redis_host" {
  value = yandex_mdb_redis_cluster.this.host[0].fqdn
}

output "redis_port" {
  value = 6380 # порт Yandex Managed Redis (TLS) всегда 6380
}

output "kafka_bootstrap_servers" {
  description = "Стандартный bootstrap-адрес Yandex Managed Kafka — SASL_SSL, порт 9091"
  value       = "bootstrap.${yandex_mdb_kafka_cluster.this.id}.mdb.yandexcloud.net:9091"
}
