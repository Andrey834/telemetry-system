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
