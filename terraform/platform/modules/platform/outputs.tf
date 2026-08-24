output "grafana_namespace" {
  value = kubernetes_namespace.monitoring.metadata[0].name
}

output "argocd_namespace" {
  value = kubernetes_namespace.argocd.metadata[0].name
}

output "argocd_port_forward_command" {
  # insecure=true больше не задаём (marketplace-продукт перестал принимать этот ключ) — ArgoCD
  # работает с дефолтным TLS, поэтому порт 443 и https://localhost:8081, не http.
  value = "kubectl -n ${kubernetes_namespace.argocd.metadata[0].name} port-forward svc/argocd-server 8081:443"
}

output "grafana_port_forward_command" {
  value = "kubectl -n ${kubernetes_namespace.monitoring.metadata[0].name} port-forward svc/kube-prometheus-stack-grafana 3000:80"
}
