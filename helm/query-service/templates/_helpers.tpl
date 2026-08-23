{{- define "query-service.name" -}}
{{- .Chart.Name -}}
{{- end -}}

{{- define "query-service.fullname" -}}
{{- printf "%s-%s" .Release.Name (include "query-service.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "query-service.labels" -}}
app.kubernetes.io/name: {{ include "query-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "query-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "query-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
