output "argocd_port_forward_command" {
  value = module.platform.argocd_port_forward_command
}

output "grafana_port_forward_command" {
  value = module.platform.grafana_port_forward_command
}

output "ingress_nginx_ip" {
  description = "Привяжите A-записи доменов к этому IP, прежде чем включать enable_cert_manager"
  value       = module.platform.ingress_nginx_ip
}
