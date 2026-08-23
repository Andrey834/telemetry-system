output "argocd_port_forward_command" {
  value = "kubectl --kubeconfig ../kubeconfig.yaml -n ${kubernetes_namespace.argocd.metadata[0].name} port-forward svc/argocd-server 8081:80"
}

output "argocd_admin_password_command" {
  description = "Пароль admin по умолчанию — секрет argocd-initial-admin-secret, живёт до первой смены пароля"
  value       = "kubectl --kubeconfig ../kubeconfig.yaml -n ${kubernetes_namespace.argocd.metadata[0].name} get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d"
}
