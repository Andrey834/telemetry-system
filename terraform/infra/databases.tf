# Managed Postgres/Redis/Kafka — в той же сети и подсети, что и Kubernetes-кластер, приватные
# (без публичного IP), приложения обращаются к ним по внутренней сети.

resource "yandex_mdb_postgresql_cluster" "this" {
  name        = "${var.cluster_name}-postgres"
  environment = "PRODUCTION"
  network_id  = module.network.network_id

  config {
    version = 16
    resources {
      resource_preset_id = "s2.micro"
      disk_type_id       = "network-ssd"
      disk_size          = 16
    }
  }

  maintenance_window {
    type = "ANYTIME"
  }

  host {
    zone      = var.default_zone
    subnet_id = module.network.subnet_id
  }
}

resource "yandex_mdb_postgresql_user" "telemetry" {
  cluster_id = yandex_mdb_postgresql_cluster.this.id
  name       = "telemetry"
  password   = var.postgres_password
}

resource "yandex_mdb_postgresql_database" "telemetry" {
  cluster_id = yandex_mdb_postgresql_cluster.this.id
  name       = "telemetry"
  owner      = yandex_mdb_postgresql_user.telemetry.name
}

# Yandex Cloud больше не создаёт новые кластеры "Managed Redis" как отдельный сервис — консоль
# показывает только "Managed Service for Valkey" (форк Redis). Terraform-ресурс называется
# yandex_mdb_redis_cluster_v2 (для обратной совместимости имени), но subcategory в его же
# документации — "Managed Service for ValKey"; версия указывается с суффиксом "-valkey".
# Порт фиксирован — 6379, TLS не включаем (tls_enabled по умолчанию false, как и у Timeweb).
resource "yandex_mdb_redis_cluster_v2" "this" {
  name        = "${var.cluster_name}-redis"
  environment = "PRODUCTION"
  network_id  = module.network.network_id

  config = {
    password = var.redis_password
    version  = "7.2-valkey"
  }

  resources = {
    resource_preset_id = "b3-c1-m4" # hm1.nano недоступен в ru-central1-d — сверено через yc managed-redis resource-preset list
    disk_size          = 16
  }

  hosts = {
    "main" = {
      zone      = var.default_zone
      subnet_id = module.network.subnet_id
    }
  }

  maintenance_window = {
    type = "ANYTIME"
  }
}

resource "yandex_mdb_kafka_cluster" "this" {
  name        = "${var.cluster_name}-kafka"
  environment = "PRODUCTION"
  network_id  = module.network.network_id
  subnet_ids  = [module.network.subnet_id]

  config {
    version          = "4.2" # актуальная (проверено через ошибку API — 3.5 недоступна)
    brokers_count    = 1
    zones            = [var.default_zone]
    assign_public_ip = false

    kafka {
      resources {
        resource_preset_id = "s2.micro"
        disk_type_id       = "network-ssd"
        disk_size          = 32
      }
    }
  }
}

resource "yandex_mdb_kafka_topic" "telemetry_raw" {
  cluster_id         = yandex_mdb_kafka_cluster.this.id
  name               = "telemetry.raw"
  partitions         = 4
  replication_factor = 1
}

# Инлайновый user {} внутри yandex_mdb_kafka_cluster устарел — провайдер требует отдельный ресурс.
resource "yandex_mdb_kafka_user" "telemetry" {
  cluster_id = yandex_mdb_kafka_cluster.this.id
  name       = "telemetry"
  password   = var.kafka_password

  permission {
    topic_name = yandex_mdb_kafka_topic.telemetry_raw.name
    role       = "ACCESS_ROLE_PRODUCER"
  }

  permission {
    topic_name = yandex_mdb_kafka_topic.telemetry_raw.name
    role       = "ACCESS_ROLE_CONSUMER"
  }
}
