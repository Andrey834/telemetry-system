output "registry_id" {
  value = yandex_container_registry.this.id
}

output "puller_service_account_id" {
  value = yandex_iam_service_account.puller.id
}
