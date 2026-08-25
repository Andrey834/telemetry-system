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
  # host[].role вычисляется сервером (MASTER/REPLICA) — индекс 0 не гарантированно primary
  # с двумя host{} в кластере, фильтруем явно.
  value = [for h in yandex_mdb_postgresql_cluster.this.host : h.fqdn if h.role == "MASTER"][0]
}

output "postgres_replica_host" {
  description = "Read-реплика — сюда ходят query-service/ingestion-service, не мешая записи telemetry-processor в primary"
  value       = [for h in yandex_mdb_postgresql_cluster.this.host : h.fqdn if h.role == "REPLICA"][0]
}

output "postgres_port" {
  value = 6432 # порт Yandex Managed PostgreSQL всегда 6432 (через встроенный connection pooler)
}

output "redis_host" {
  value = yandex_mdb_redis_cluster_v2.this.hosts["main"].fqdn
}

output "redis_port" {
  value = 6379 # фиксированный порт, не настраивается (tls_enabled не включаем)
}

output "kafka_bootstrap_servers" {
  # host[].name — FQDN брокера. SASL_SSL, порт 9091.
  value = "${tolist(yandex_mdb_kafka_cluster.this.host)[0].name}:9091"
}

output "postgres_password" {
  value     = var.postgres_password
  sensitive = true
}

output "redis_password" {
  value     = var.redis_password
  sensitive = true
}

output "kafka_password" {
  value     = var.kafka_password
  sensitive = true
}
