# Читаем output'ы ../infra напрямую из его state (оба state локальные — просто файлы на диске,
# удалённый backend не нужен). Так реальные хосты БД/registry_id/пароли попадают в ArgoCD
# Application'ы автоматически, без ручного копирования значений в YAML.
data "terraform_remote_state" "infra" {
  backend = "local"
  config = {
    path = "${path.module}/../infra/terraform.tfstate"
  }
}

# Публичный корневой сертификат Yandex Cloud для Managed-баз — нужен клиенту Kafka (SASL_SSL),
# иначе PKIX path building failed (default JVM truststore его не знает).
data "http" "yandex_ca" {
  url = "https://storage.yandexcloud.net/cloud-certs/CA.pem"
}

locals {
  registry_id      = data.terraform_remote_state.infra.outputs.registry_id
  kafka_bootstrap  = data.terraform_remote_state.infra.outputs.kafka_bootstrap_servers
  postgres_host    = data.terraform_remote_state.infra.outputs.postgres_host
  postgres_replica = data.terraform_remote_state.infra.outputs.postgres_replica_host
  redis_host       = data.terraform_remote_state.infra.outputs.redis_host

  dashboard_host         = var.dashboard_host
  query_service_host     = var.query_service_host
  ingestion_service_host = var.ingestion_service_host
  cluster_issuer         = "letsencrypt-prod"

  # Ноды Yandex Managed Kubernetes аутентифицируются в cr.yandex через собственный сервисный
  # аккаунт (роль container-registry.images.puller) — imagePullSecrets не нужен.
  argocd_apps = {
    ingestion-service = {
      image = "cr.yandex/${local.registry_id}/ingestion-service"
      env = {
        kafkaBootstrapServers = local.kafka_bootstrap
        kafkaSecurityProtocol = "SASL_SSL"
        kafkaUsername         = "telemetry"
        dbReplicaHost         = local.postgres_replica
        dbPort                = "6432"
        dbSslMode             = "require"
      }
      credentialsSecretName = "ingestion-service-credentials"
      ingress = var.enable_cert_manager ? {
        enabled       = true
        className     = "nginx"
        host          = local.ingestion_service_host
        clusterIssuer = local.cluster_issuer
        tlsSecretName = "ingestion-service-tls"
      } : null
    }
    telemetry-processor = {
      image = "cr.yandex/${local.registry_id}/telemetry-processor"
      env = {
        kafkaBootstrapServers = local.kafka_bootstrap
        kafkaSecurityProtocol = "SASL_SSL"
        kafkaUsername         = "telemetry"
        redisHost             = local.redis_host
        dbHost                = local.postgres_host
        dbPort                = "6432"
        dbSslMode             = "require"
        dbSslEnabled          = "true"
      }
      credentialsSecretName = "telemetry-processor-credentials"
      ingress               = null
    }
    query-service = {
      image = "cr.yandex/${local.registry_id}/query-service"
      env = {
        redisHost          = local.redis_host
        corsAllowedOrigins = "https://${local.dashboard_host}"
        dbReplicaHost      = local.postgres_replica
        dbPort             = "6432"
        dbSslMode          = "require"
      }
      credentialsSecretName = "query-service-credentials"
      # Пока enable_cert_manager=false (первый этап — только инфра/платформа, домен ещё не
      # привязан к ingress_nginx_ip), Ingress не создаём вообще: ссылка на несуществующий
      # ClusterIssuer уйдёт в вечный pending. Включаем вместе с cert-manager вторым apply,
      # когда DNS уже готов.
      ingress = var.enable_cert_manager ? {
        enabled       = true
        className     = "nginx"
        host          = local.query_service_host
        clusterIssuer = local.cluster_issuer
        tlsSecretName = "query-service-tls"
      } : null
    }
    dashboard = {
      image = "cr.yandex/${local.registry_id}/dashboard"
      env = {
        queryServiceUrl = "https://${local.query_service_host}"
      }
      credentialsSecretName = null
      # Basic Auth на Ingress убран — авторизация теперь на уровне приложения (JWT-логин через
      # query-service /auth/login), см. SecurityConfig в query-service.
      ingress = var.enable_cert_manager ? {
        enabled       = true
        className     = "nginx"
        host          = local.dashboard_host
        clusterIssuer = local.cluster_issuer
        tlsSecretName = "dashboard-tls"
      } : null
    }
  }

  argocd_secrets = {
    ingestion-service-credentials = {
      DB_PASSWORD       = data.terraform_remote_state.infra.outputs.postgres_password
      KAFKA_PASSWORD    = data.terraform_remote_state.infra.outputs.kafka_password
      KAFKA_SSL_CA_CERT = data.http.yandex_ca.response_body
    }
    telemetry-processor-credentials = {
      DB_PASSWORD       = data.terraform_remote_state.infra.outputs.postgres_password
      REDIS_PASSWORD    = data.terraform_remote_state.infra.outputs.redis_password
      KAFKA_PASSWORD    = data.terraform_remote_state.infra.outputs.kafka_password
      KAFKA_SSL_CA_CERT = data.http.yandex_ca.response_body
    }
    query-service-credentials = {
      DB_PASSWORD    = data.terraform_remote_state.infra.outputs.postgres_password
      REDIS_PASSWORD = data.terraform_remote_state.infra.outputs.redis_password
      JWT_SECRET     = var.jwt_secret
    }
  }
}

resource "kubernetes_namespace" "telemetry_system" {
  metadata {
    name = "telemetry-system"
  }
}

resource "kubernetes_secret" "app_credentials" {
  for_each = local.argocd_secrets

  metadata {
    name      = each.key
    namespace = kubernetes_namespace.telemetry_system.metadata[0].name
  }

  data = each.value
}

resource "kubectl_manifest" "argocd_app" {
  for_each = local.argocd_apps

  yaml_body = yamlencode({
    apiVersion = "argoproj.io/v1alpha1"
    kind       = "Application"
    metadata = {
      name      = each.key
      namespace = "argocd"
    }
    spec = {
      project = "default"
      source = {
        repoURL        = "https://github.com/Andrey834/telemetry-system.git"
        targetRevision = "main"
        path           = "helm/${each.key}"
        helm = {
          valuesObject = merge(
            {
              image            = { repository = each.value.image, tag = var.image_tag }
              imagePullSecrets = []
              env              = each.value.env
            },
            each.value.credentialsSecretName == null ? {} : {
              credentialsSecretName = each.value.credentialsSecretName
            },
            lookup(each.value, "ingress", null) == null ? {} : {
              ingress = each.value.ingress
            }
          )
        }
      }
      destination = {
        server    = "https://kubernetes.default.svc"
        namespace = "telemetry-system"
      }
      syncPolicy = {
        automated = {
          prune    = true
          selfHeal = true
        }
        syncOptions = ["CreateNamespace=true"]
      }
    }
  })

  depends_on = [module.platform, kubernetes_secret.app_credentials, null_resource.dns_record]
}
