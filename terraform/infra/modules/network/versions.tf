terraform {
  required_providers {
    # Дочерние модули не наследуют source от корневого required_providers автоматически —
    # без явного указания здесь Terraform считает "yandex" провайдером hashicorp/yandex
    # (легаси-дефолт для неймспейса), которого не существует, и init падает.
    yandex = {
      source = "yandex-cloud/yandex"
    }
  }
}
