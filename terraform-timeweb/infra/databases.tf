# Все три кластера БД — в той же VPC (twc_vpc.this), что и Kubernetes-кластер, без публичного
# IP: приложения обращаются к ним по приватной сети, не через интернет.

# data-source и resource используют разные словари: у пресетов type общий ("postgres"), у
# самого кластера — версионный ("postgres17") — подтверждено ответом API /api/v1/presets/dbs.
data "twc_database_preset" "postgres" {
  location = var.location
  type     = "postgres"
  disk     = var.postgres_disk_mb
}

resource "twc_database_cluster" "postgres" {
  name      = "${var.cluster_name}-postgres"
  type      = "postgres17"
  preset_id = data.twc_database_preset.postgres.id

  network {
    id = twc_vpc.this.id
  }
}

resource "twc_database_instance" "postgres_telemetry" {
  cluster_id = twc_database_cluster.postgres.id
  name       = "telemetry"
}

resource "twc_database_user" "postgres_telemetry" {
  cluster_id = twc_database_cluster.postgres.id
  login      = "telemetry"
  password   = var.postgres_password
  for_all    = true
  privileges = ["SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "REFERENCES", "TRIGGER", "TEMPORARY"]
}

data "twc_database_preset" "redis" {
  location = var.location
  type     = "redis"
  disk     = 10240 # самый дешёвый доступный пресет redis в ru-1 (id 3683, 1 CPU/2 ГБ/10 ГБ, 670₽)
  ram      = 2048  # disk=10240 без ram совпадает ещё и с id 3691 (4 ГБ RAM, 970₽) — уточняем
}

resource "twc_database_cluster" "redis" {
  name      = "${var.cluster_name}-redis"
  type      = "redis7"
  preset_id = data.twc_database_preset.redis.id

  network {
    id = twc_vpc.this.id
  }
}

resource "twc_database_user" "redis" {
  cluster_id = twc_database_cluster.redis.id
  login      = "telemetry"
  password   = var.redis_password
  for_all    = true
  privileges = ["READ", "WRITE"]
}

data "twc_database_preset" "kafka" {
  location = var.location
  type     = "kafka"
}

resource "twc_database_cluster" "kafka" {
  name      = "${var.cluster_name}-kafka"
  type      = "kafka"
  preset_id = data.twc_database_preset.kafka.id

  network {
    id = twc_vpc.this.id
  }
}

# У Timeweb топик Kafka — это "instance" внутри database cluster (тот же ресурс, что и база
# данных для Postgres/MySQL). Автоматически создаётся только default_topic — свой топик нужно
# завести явно, иначе приложение подписывается на несуществующий topic.
resource "twc_database_instance" "kafka_telemetry_raw" {
  cluster_id = twc_database_cluster.kafka.id
  name       = "telemetry.raw"
}

resource "twc_database_user" "kafka" {
  cluster_id = twc_database_cluster.kafka.id
  login      = "telemetry"
  password   = var.kafka_password
  for_all    = true
  # Точные допустимые значения для Kafka в документации провайдера не перечислены (только
  # SQL/Redis) — если API отклонит apply, проверьте актуальный список в консоли Timeweb.
  privileges = ["READ", "WRITE"]
}
