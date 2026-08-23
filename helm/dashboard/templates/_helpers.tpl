{{- define "dashboard.name" -}}
{{- .Chart.Name -}}
{{- end -}}

{{- define "dashboard.fullname" -}}
{{- printf "%s-%s" .Release.Name (include "dashboard.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "dashboard.labels" -}}
app.kubernetes.io/name: {{ include "dashboard.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "dashboard.selectorLabels" -}}
app.kubernetes.io/name: {{ include "dashboard.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
