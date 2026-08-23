resource "yandex_iam_service_account" "cluster" {
  name      = "${var.cluster_name}-cluster-sa"
  folder_id = var.folder_id
}

resource "yandex_resourcemanager_folder_iam_member" "cluster_editor" {
  folder_id = var.folder_id
  role      = "k8s.clusters.agent"
  member    = "serviceAccount:${yandex_iam_service_account.cluster.id}"
}

resource "yandex_resourcemanager_folder_iam_member" "cluster_vpc" {
  folder_id = var.folder_id
  role      = "vpc.publicAdmin"
  member    = "serviceAccount:${yandex_iam_service_account.cluster.id}"
}

resource "yandex_resourcemanager_folder_iam_member" "cluster_lb" {
  folder_id = var.folder_id
  role      = "load-balancer.admin"
  member    = "serviceAccount:${yandex_iam_service_account.cluster.id}"
}

resource "yandex_iam_service_account" "node" {
  name      = "${var.cluster_name}-node-sa"
  folder_id = var.folder_id
}

resource "yandex_resourcemanager_folder_iam_member" "node_puller" {
  folder_id = var.folder_id
  role      = "container-registry.images.puller"
  member    = "serviceAccount:${yandex_iam_service_account.node.id}"
}

resource "yandex_resourcemanager_folder_iam_member" "node_editor" {
  folder_id = var.folder_id
  role      = "compute.editor"
  member    = "serviceAccount:${yandex_iam_service_account.node.id}"
}

resource "yandex_kubernetes_cluster" "this" {
  name        = var.cluster_name
  network_id  = var.network_id
  description = "Кластер для telemetry-system: приём и обработка телеметрии в реальном времени"

  master {
    version = var.k8s_version

    zonal {
      zone      = var.zone
      subnet_id = var.subnet_id
    }

    public_ip          = true
    security_group_ids = [var.security_group_id]

    maintenance_policy {
      auto_upgrade = true
      maintenance_window {
        start_time = "22:00"
        duration   = "3h"
      }
    }
  }

  service_account_id      = yandex_iam_service_account.cluster.id
  node_service_account_id = yandex_iam_service_account.node.id

  release_channel = "STABLE"

  depends_on = [
    yandex_resourcemanager_folder_iam_member.cluster_editor,
    yandex_resourcemanager_folder_iam_member.cluster_vpc,
    yandex_resourcemanager_folder_iam_member.cluster_lb,
  ]
}

resource "yandex_kubernetes_node_group" "default" {
  cluster_id = yandex_kubernetes_cluster.this.id
  name       = "${var.cluster_name}-nodes"

  version = var.k8s_version

  instance_template {
    platform_id = "standard-v3"

    resources {
      cores  = var.node_cores
      memory = var.node_memory
    }

    boot_disk {
      type = "network-ssd"
      size = 64
    }

    network_interface {
      subnet_ids         = [var.subnet_id]
      security_group_ids = [var.security_group_id, var.default_security_group_id]
      nat                = true
    }

    scheduling_policy {
      preemptible = var.preemptible
    }
  }

  scale_policy {
    fixed_scale {
      size = var.node_count
    }
  }

  allocation_policy {
    location {
      zone = var.zone
    }
  }

  maintenance_policy {
    auto_upgrade = true
    auto_repair  = true

    maintenance_window {
      start_time = "22:00"
      duration   = "3h"
    }
  }
}


