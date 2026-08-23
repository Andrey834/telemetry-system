output "cluster_id" {
  description = "Понадобится в ../platform/terraform.tfvars как var.cluster_id"
  value       = twc_k8s_cluster.this.id
}

output "cluster_status" {
  value = twc_k8s_cluster.this.status
}

output "kubeconfig_path" {
  description = "kubeconfig уже записан локально этой стадией — для kubectl: export KUBECONFIG=$(terraform output -raw kubeconfig_path)"
  value       = local_sensitive_file.kubeconfig.filename
}

output "postgres_host" {
  value = twc_database_cluster.postgres.networks[0].ips[0].ip
}

output "postgres_port" {
  value = twc_database_cluster.postgres.port
}

output "redis_host" {
  value = twc_database_cluster.redis.networks[0].ips[0].ip
}

output "redis_port" {
  value = twc_database_cluster.redis.port
}

output "kafka_host" {
  value = twc_database_cluster.kafka.networks[0].ips[0].ip
}

output "kafka_port" {
  value = twc_database_cluster.kafka.port
}
