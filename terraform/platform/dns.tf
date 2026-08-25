# Автоматическое управление A-записями на reg.ru через REG.API v2 (api.reg.ru) — официального
# Terraform-провайдера у reg.ru нет, поэтому дёргаем API через curl в local-exec. Авторизация —
# клиентский SSL-сертификат + логин/пароль; вызывающий IP должен быть в white list API в личном
# кабинете reg.ru (настраивается один раз вручную, Terraform-ресурса для этого нет).
#
# reg.ru не поддерживает update записи напрямую — remove_record (игнорируем ошибку "не найдено"
# при первом запуске) + add_alias с новым IP.

locals {
  dns_records = var.dns_zone == null ? {} : {
    dashboard         = trimsuffix(var.dashboard_host, ".${var.dns_zone}")
    query-service     = trimsuffix(var.query_service_host, ".${var.dns_zone}")
    ingestion-service = trimsuffix(var.ingestion_service_host, ".${var.dns_zone}")
  }
}

resource "null_resource" "dns_record" {
  for_each = local.dns_records

  # cert_path/key_path/username/password тоже приходится класть в triggers — destroy-провизионер
  # может ссылаться только на self, обращаться к var.* напрямую в нём нельзя.
  triggers = {
    ip        = module.platform.ingress_nginx_ip
    subdomain = each.value
    zone      = var.dns_zone
    cert_path = var.regru_cert_path
    key_path  = var.regru_key_path
    username  = var.regru_username
    password  = var.regru_password
  }

  provisioner "local-exec" {
    command = <<-EOT
      set -e
      curl -s --cert "${var.regru_cert_path}" --key "${var.regru_key_path}" \
        -d "username=${var.regru_username}" \
        -d "password=${var.regru_password}" \
        -d "input_format=json" -d "output_format=json" \
        --data-urlencode 'input_data={"domains":[{"dname":"${var.dns_zone}"}],"subdomain":"${each.value}","record_type":"A"}' \
        https://api.reg.ru/api/regru2/zone/remove_record > /dev/null

      curl -sf --cert "${var.regru_cert_path}" --key "${var.regru_key_path}" \
        -d "username=${var.regru_username}" \
        -d "password=${var.regru_password}" \
        -d "input_format=json" -d "output_format=json" \
        --data-urlencode 'input_data={"domains":[{"dname":"${var.dns_zone}"}],"subdomain":"${each.value}","ipaddr":"${module.platform.ingress_nginx_ip}"}' \
        https://api.reg.ru/api/regru2/zone/add_alias
    EOT
  }

  provisioner "local-exec" {
    when    = destroy
    command = <<-EOT
      curl -s --cert "${self.triggers.cert_path}" --key "${self.triggers.key_path}" \
        -d "username=${self.triggers.username}" \
        -d "password=${self.triggers.password}" \
        -d "input_format=json" -d "output_format=json" \
        --data-urlencode 'input_data={"domains":[{"dname":"${self.triggers.zone}"}],"subdomain":"${self.triggers.subdomain}","record_type":"A"}' \
        https://api.reg.ru/api/regru2/zone/remove_record || true
    EOT
  }
}

# Внешний резолвер, которым пользуется Yandex Managed Kubernetes (CoreDNS форвардит на него через
# /etc/resolv.conf), держит очень долгоживущий кэш — после смены A-записи (например, IP
# ingress-nginx поменялся после пересоздания LB) кластер изнутри может резолвить наши домены в
# устаревший IP часами, из-за чего самопроверка HTTP-01 у cert-manager зависает. coredns-user —
# штатный EnsureExists-ConfigMap для пользовательских правок CoreDNS (не перезатирается
# addon-manager'ом), сюда добавляем статичные hosts-записи — кластер больше не зависит от
# внешнего резолвера при обращении сам к себе.
resource "kubernetes_config_map_v1_data" "coredns_user" {
  count = var.dns_zone == null ? 0 : 1

  metadata {
    name      = "coredns-user"
    namespace = "kube-system"
  }

  data = {
    Corefile = <<-EOT
      ${var.dns_zone}:53 {
          hosts {
              ${module.platform.ingress_nginx_ip} ${var.dashboard_host}
              ${module.platform.ingress_nginx_ip} ${var.query_service_host}
              ${module.platform.ingress_nginx_ip} ${var.ingestion_service_host}
              fallthrough
          }
          forward . /etc/resolv.conf
      }
    EOT
  }

  force = true
}
