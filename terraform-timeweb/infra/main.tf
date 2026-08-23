resource "twc_vpc" "this" {
  name      = "${var.cluster_name}-vpc"
  location  = var.location
  subnet_v4 = var.vpc_subnet_cidr
}

data "twc_k8s_preset" "master" {
  cpu      = var.master_cpu
  type     = "master"
  location = var.location
}

data "twc_k8s_preset" "worker" {
  cpu      = var.worker_cpu
  type     = "worker"
  location = var.location
}

# ingress = true ставит встроенный ingress-контроллер вместе с кластером.
resource "twc_k8s_cluster" "this" {
  name        = var.cluster_name
  description = "Кластер для telemetry-system, прием и обработка телеметрии в реальном времени"

  high_availability = var.high_availability
  version           = var.k8s_version
  network_driver    = var.network_driver
  ingress           = true

  preset_id  = data.twc_k8s_preset.master.id
  network_id = twc_vpc.this.id

  maintenance_slot {
    type = "any_time"
  }
}

resource "twc_k8s_node_group" "default" {
  cluster_id = twc_k8s_cluster.this.id
  name       = "${var.cluster_name}-nodes"

  preset_id  = data.twc_k8s_preset.worker.id
  node_count = var.worker_count

  is_autohealing = true
}

# У Timeweb нет data-source для чтения уже существующего кластера — пишем готовый kubeconfig
# на диск здесь, ../platform читает файл напрямую через provider.kubernetes.config_path.
resource "local_sensitive_file" "kubeconfig" {
  content  = twc_k8s_cluster.this.kubeconfig
  filename = "${path.module}/../kubeconfig.yaml"

  depends_on = [twc_k8s_node_group.default]
}
