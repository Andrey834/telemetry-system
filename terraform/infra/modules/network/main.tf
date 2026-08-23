resource "yandex_vpc_network" "this" {
  name = "${var.cluster_name}-network"
}

resource "yandex_vpc_subnet" "this" {
  name           = "${var.cluster_name}-subnet"
  zone           = var.zone
  network_id     = yandex_vpc_network.this.id
  v4_cidr_blocks = [var.subnet_cidr]
}

resource "yandex_vpc_address" "ingress" {
  name = "${var.cluster_name}-ingress-ip"
  external_ipv4_address {
    zone_id = var.zone
  }
}

# Единая security group на мастер и на ноды — раздельные группы приводят к тому, что внешний
# доступ к мастеру не появляется, даже если cross-group правила и healthcheck зеркалированы.
resource "yandex_vpc_security_group" "k8s" {
  name        = "${var.cluster_name}-k8s-sg"
  folder_id   = var.folder_id
  network_id  = yandex_vpc_network.this.id
  description = "Единая SG для мастера и нод Managed Kubernetes"

  ingress {
    protocol          = "TCP"
    description       = "Healthcheck от Yandex Network Load Balancer"
    predefined_target = "loadbalancer_healthchecks"
    from_port         = 0
    to_port           = 65535
  }

  ingress {
    protocol          = "ANY"
    description       = "Взаимодействие мастера и нод внутри кластера"
    predefined_target = "self_security_group"
    from_port         = 0
    to_port           = 65535
  }

  ingress {
    protocol       = "ANY"
    description    = "Трафик от подов и сервисов кластера к нодам"
    v4_cidr_blocks = ["10.112.0.0/16", "10.96.0.0/16"]
    from_port      = 0
    to_port        = 65535
  }

  ingress {
    protocol       = "ICMP"
    description    = "ICMP между приватными диапазонами — диагностика"
    v4_cidr_blocks = ["10.0.0.0/8", "192.168.0.0/16", "172.16.0.0/12"]
  }

  ingress {
    protocol       = "TCP"
    description    = "NodePort-диапазон для LoadBalancer/NodePort сервисов"
    v4_cidr_blocks = ["0.0.0.0/0"]
    from_port      = 30000
    to_port        = 32767
  }

  ingress {
    protocol       = "TCP"
    description    = "SSH к нодам"
    v4_cidr_blocks = ["0.0.0.0/0"]
    port           = 22
  }

  # Yandex Cloud требует оба порта для доступа к мастеру — 443 и 6443, не только реальный
  # порт из kubeconfig (managed-kubernetes/operations/connect/security-groups).
  ingress {
    protocol       = "TCP"
    description    = "Доступ к Kubernetes API, порт 443"
    v4_cidr_blocks = ["0.0.0.0/0"]
    port           = 443
  }

  ingress {
    protocol       = "TCP"
    description    = "Доступ к Kubernetes API, порт 6443"
    v4_cidr_blocks = ["0.0.0.0/0"]
    port           = 6443
  }

  egress {
    protocol       = "ANY"
    description    = "Весь исходящий трафик"
    v4_cidr_blocks = ["0.0.0.0/0"]
    from_port      = 0
    to_port        = 65535
  }
}
