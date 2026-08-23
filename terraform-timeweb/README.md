# Timeweb Cloud — альтернативная инфраструктура

Отдельная от `../terraform` (Yandex Cloud) реализация того же проекта на
[Timeweb Cloud](https://timeweb.cloud/), провайдер `timeweb-cloud/timeweb-cloud`. Structурно так же
разделена на две стадии с раздельным state — по тем же причинам, что и Yandex-версия: не создавать
кластер и не разворачивать в него ресурсы через kubernetes/helm-провайдеры в одном apply.

Отличия от Yandex-версии, упрощающие конфиг:

- `kubeconfig` — готовый атрибут ресурса `twc_k8s_cluster`, не нужно вручную угадывать
  порт/endpoint (в Yandex пришлось разбираться с `external_v4_endpoint`, портом 6443 и т.д.).
- Нет отдельного data-source для чтения уже существующего кластера — вместо этого стадия `infra/`
  сама пишет kubeconfig на диск (`../kubeconfig.yaml`), а `platform/` читает готовый файл через
  `config_path`.
- `cert-manager` и `kube-prometheus-stack` — штатные addon'ы кластера (`twc_k8s_addon`), не нужно
  городить обход через маркетплейс-чарты, как для Yandex (там quay.io недоступен с нод).
- Ingress-контроллер включается флагом `ingress = true` на самом кластере — отдельный
  `helm_release` для ingress-nginx не нужен.
- У `twc_firewall` нет поддержки привязки к Kubernetes-ресурсам (только `server`/`dbaas`/
  `balancer`/`app`) — сетевую безопасность кластера/node group Timeweb, судя по всему, полностью
  берёт на себя сам managed-сервис, вручную настраивать нечего.

## Запуск

```bash
export TWC_TOKEN=<токен с https://timeweb.cloud/my/api-keys>
# Важно: у токена должно быть отключено подтверждение удаления серверов через Telegram,
# иначе terraform destroy не сможет ничего удалить.

cd infra
cp terraform.tfvars.example terraform.tfvars   # при необходимости поменяйте значения
terraform init
terraform plan
terraform apply

cd ../platform
cp terraform.tfvars.example terraform.tfvars
# cluster_id — из вывода предыдущего шага:
#   cd ../infra && terraform output -raw cluster_id
terraform init
terraform plan
terraform apply
```

После обеих стадий:

```bash
export KUBECONFIG=$(cd infra && terraform output -raw kubeconfig_path)
kubectl get nodes
```

## Непроверенное на практике

Этот конфиг написан и провалидирован (`terraform validate`) по документации провайдера, но ни разу
не применялся к реальной инфраструктуре Timeweb Cloud — в отличие от `../terraform` (Yandex),
прошедшего десятки циклов `apply`/`destroy`. Перед тем как полагаться на него:

- Проверьте актуальную версию Kubernetes (`k8s_version` в `infra/terraform.tfvars`) —
  https://api.timeweb.cloud/api/v1/k8s/k8s_versions.
- `config_type = "basic"` для addon'ов `cert-manager`/`kube-prometheus-stack` использует дефолты
  Timeweb — кастомизация через `config_type = "custom"` + `yaml_config` не проверена (формат
  `yaml_config` для каждого типа addon не задокументирован построчно в схеме провайдера).
- Реального теста на доступность внешних registry (quay.io и т.п.) с нод Timeweb не было — если
  `helm_release.argocd` даст `ImagePullBackOff`, смотрите `../terraform/modules/platform` за
  примером обхода через зеркало реестра (там же описана история проблемы для Yandex).
