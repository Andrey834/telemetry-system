output "grafana_namespace" {
  value = kubernetes_namespace.monitoring.metadata[0].name
}

output "argocd_namespace" {
  value = kubernetes_namespace.argocd.metadata[0].name
}

output "argocd_port_forward_command" {
  value = "kubectl -n ${kubernetes_namespace.argocd.metadata[0].name} port-forward svc/argocd-server 8081:80"
}

output "grafana_port_forward_command" {
  value = "kubectl -n ${kubernetes_namespace.monitoring.metadata[0].name} port-forward svc/kube-prometheus-stack-grafana 3000:80"
}

output "ingress_nginx_ip" {
  description = "Внешний IP ingress-nginx — привяжите к нему A-записи доменов перед тем, как включать enable_cert_manager"
  value       = data.kubernetes_service.ingress_nginx_controller.status[0].load_balancer[0].ingress[0].ip
}
