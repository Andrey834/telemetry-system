module "network" {
  source = "./modules/network"

  cluster_name = var.cluster_name
  zone         = var.default_zone
  folder_id    = var.yc_folder_id
}

module "registry" {
  source = "./modules/registry"

  cluster_name = var.cluster_name
  folder_id    = var.yc_folder_id
}

module "kubernetes" {
  source = "./modules/kubernetes"

  cluster_name              = var.cluster_name
  folder_id                 = var.yc_folder_id
  zone                      = var.default_zone
  network_id                = module.network.network_id
  subnet_id                 = module.network.subnet_id
  security_group_id         = module.network.security_group_id
  default_security_group_id = module.network.default_security_group_id
  node_count                = var.node_count
  node_cores                = var.node_cores
  node_memory               = var.node_memory
  preemptible               = var.preemptible
  registry_id               = module.registry.registry_id
}
