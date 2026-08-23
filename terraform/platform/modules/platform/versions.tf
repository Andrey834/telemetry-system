terraform {
  required_providers {
    # Без явного source здесь Terraform для неявной ссылки на "yandex" подставляет легаси-адрес
    # hashicorp/yandex (не существует) — та же причина, что была в модулях network/registry/kubernetes.
    yandex = {
      source = "yandex-cloud/yandex"
    }
  }
}
