{{- define "telemetry-processor.name" -}}
{{- .Chart.Name -}}
{{- end -}}

{{- define "telemetry-processor.fullname" -}}
{{- printf "%s-%s" .Release.Name (include "telemetry-processor.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "telemetry-processor.labels" -}}
app.kubernetes.io/name: {{ include "telemetry-processor.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "telemetry-processor.selectorLabels" -}}
app.kubernetes.io/name: {{ include "telemetry-processor.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
