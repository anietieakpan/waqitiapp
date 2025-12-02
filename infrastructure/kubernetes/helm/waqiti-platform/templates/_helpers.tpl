{{/*
Expand the name of the chart.
*/}}
{{- define "waqiti-platform.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "waqiti-platform.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "waqiti-platform.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "waqiti-platform.labels" -}}
helm.sh/chart: {{ include "waqiti-platform.chart" . }}
{{ include "waqiti-platform.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "waqiti-platform.selectorLabels" -}}
app.kubernetes.io/name: {{ include "waqiti-platform.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "waqiti-platform.serviceAccountName" -}}
{{- if .Values.security.serviceAccounts.create }}
{{- default (include "waqiti-platform.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Redis host
*/}}
{{- define "waqiti-platform.redis.host" -}}
{{- if .Values.redis.enabled }}
{{- printf "%s-redis-master" (include "waqiti-platform.fullname" .) }}
{{- else }}
{{- .Values.externalRedis.host }}
{{- end }}
{{- end }}

{{/*
Redis secret name
*/}}
{{- define "waqiti-platform.redis.secretName" -}}
{{- if .Values.redis.enabled }}
{{- printf "%s-redis" (include "waqiti-platform.fullname" .) }}
{{- else }}
{{- .Values.externalRedis.existingSecret }}
{{- end }}
{{- end }}

{{/*
Redis secret key
*/}}
{{- define "waqiti-platform.redis.secretKey" -}}
{{- if .Values.redis.enabled }}
redis-password
{{- else }}
{{- .Values.externalRedis.existingSecretPasswordKey }}
{{- end }}
{{- end }}

{{/*
Kafka bootstrap servers
*/}}
{{- define "waqiti-platform.kafka.bootstrapServers" -}}
{{- if .Values.kafka.enabled }}
{{- printf "%s-kafka:9092" (include "waqiti-platform.fullname" .) }}
{{- else }}
{{- .Values.externalKafka.bootstrapServers }}
{{- end }}
{{- end }}

{{/*
PostgreSQL host
*/}}
{{- define "waqiti-platform.postgresql.host" -}}
{{- if .Values.postgresql.enabled }}
{{- printf "%s-postgresql" (include "waqiti-platform.fullname" .) }}
{{- else }}
{{- .Values.externalPostgresql.host }}
{{- end }}
{{- end }}

{{/*
PostgreSQL secret name
*/}}
{{- define "waqiti-platform.postgresql.secretName" -}}
{{- if .Values.postgresql.enabled }}
{{- printf "%s-postgresql" (include "waqiti-platform.fullname" .) }}
{{- else }}
{{- .Values.externalPostgresql.existingSecret }}
{{- end }}
{{- end }}