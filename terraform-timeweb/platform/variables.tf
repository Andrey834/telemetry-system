variable "cluster_id" {
  description = "ID кластера — cd ../infra && terraform output -raw cluster_id"
  type        = number
}

variable "enable_cert_manager" {
  description = "Устанавливает addon cert-manager (config_type=basic, дефолтные настройки Timeweb). Выпуск сертификатов Let's Encrypt всё равно требует реального домена, направленного на публичный IP ingress — сам addon не решает эту часть."
  type        = bool
  default     = true
}

variable "argocd_version" {
  description = "Версия чарта argo-helm/argo-cd — 8.0.0 соответствует версии, показанной в карточке ArgoCD-addon'а в консоли Timeweb"
  type        = string
  default     = "8.0.0"
}

# Готовый bcrypt-хэш пароля, не сам пароль (terraform bcrypt() не подходит — случайная соль
# ломает идемпотентность apply). Сгенерировать: htpasswd -nbBC 10 "" 'пароль' | tr -d ':\n' | sed 's/$2y/$2a/'
# null — используется пароль по умолчанию из секрета argocd-initial-admin-secret.
variable "argocd_admin_password_bcrypt_hash" {
  type      = string
  sensitive = true
  default   = null
}
