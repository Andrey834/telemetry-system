{{- define "ingestion-service.name" -}}
{{- .Chart.Name -}}
{{- end -}}

{{- define "ingestion-service.fullname" -}}
{{- printf "%s-%s" .Release.Name (include "ingestion-service.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "ingestion-service.labels" -}}
app.kubernetes.io/name: {{ include "ingestion-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "ingestion-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "ingestion-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
