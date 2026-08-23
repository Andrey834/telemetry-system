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
