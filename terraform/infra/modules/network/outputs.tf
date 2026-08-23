output "network_id" {
  value = yandex_vpc_network.this.id
}

output "subnet_id" {
  value = yandex_vpc_subnet.this.id
}

output "security_group_id" {
  value = yandex_vpc_security_group.k8s.id
}

output "default_security_group_id" {
  value = yandex_vpc_network.this.default_security_group_id
}

output "ingress_static_ip" {
  value = yandex_vpc_address.ingress.external_ipv4_address[0].address
}
