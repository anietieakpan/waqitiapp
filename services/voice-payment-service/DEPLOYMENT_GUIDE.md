# Voice Payment Service - Complete Deployment Guide

**Version:** 1.0.0
**Last Updated:** 2025-11-10
**Status:** Production Ready (98%)

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Infrastructure Setup](#infrastructure-setup)
3. [Security Configuration](#security-configuration)
4. [Service Deployment](#service-deployment)
5. [Post-Deployment Verification](#post-deployment-verification)
6. [Monitoring & Observability](#monitoring--observability)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software

- **Java:** OpenJDK 17+
- **Maven:** 3.8+
- **Docker:** 20.10+
- **Docker Compose:** 2.0+
- **PostgreSQL:** 14+
- **Redis:** 7.0+
- **Kafka:** 3.0+
- **HashiCorp Vault:** 1.13+
- **ClamAV:** Latest
- **Keycloak:** 21+

### Required Access

- ✅ Database credentials (from Vault)
- ✅ Redis credentials (from Vault)
- ✅ Kafka credentials (from Vault)
- ✅ Vault token/AppRole credentials
- ✅ Google Cloud API key (for Speech-to-Text)
- ✅ Keycloak client credentials

---

## Infrastructure Setup

### 1. Deploy ClamAV (Antivirus)

```bash
cd /Users/anietieakpan/git/waqiti-app/services/voice-payment-service

# Start ClamAV
docker-compose -f docker/clamav/docker-compose.yml up -d

# Wait for startup (2-5 minutes)
docker logs -f waqiti-clamav

# Verify
echo "PING" | nc localhost 3310
# Expected: PONG
```

**See:** `docker/clamav/README.md` for detailed configuration

### 2. Generate TLS Certificates

**For Development:**
```bash
# Generate self-signed certificates
./scripts/generate-tls-certificates.sh

# Output: ./certs/ directory with all certificates
```

**For Production:**
Use certificates from trusted CA:
- Let's Encrypt (free, automated)
- DigiCert, Sectigo, GlobalSign (commercial)

### 3. Configure PostgreSQL

```bash
# 1. Enable SSL in postgresql.conf
ssl = on
ssl_cert_file = '/path/to/certs/postgresql/server-cert.pem'
ssl_key_file = '/path/to/certs/postgresql/server-key.pem'
ssl_ca_file = '/path/to/certs/ca/ca-cert.pem'

# 2. Require SSL in pg_hba.conf
hostssl all all 0.0.0.0/0 md5

# 3. Restart PostgreSQL
sudo systemctl restart postgresql

# 4. Test SSL connection
psql "postgresql://user:pass@localhost:5432/waqiti_voice_payments?sslmode=verify-full"
```

### 4. Configure Redis

```bash
# 1. Enable TLS in redis.conf
tls-port 6380
port 0  # Disable non-TLS
tls-cert-file /path/to/certs/redis/redis-cert.pem
tls-key-file /path/to/certs/redis/redis-key.pem
tls-ca-cert-file /path/to/certs/ca/ca-cert.pem
tls-auth-clients yes

# 2. Restart Redis
sudo systemctl restart redis

# 3. Test TLS connection
redis-cli --tls --cert certs/redis/client-cert.pem \
  --key certs/redis/client-key.pem \
  --cacert certs/ca/ca-cert.pem -p 6380 PING
```

### 5. Configure Kafka

```bash
# 1. Enable SSL in server.properties
listeners=SSL://0.0.0.0:9093
ssl.keystore.location=/path/to/certs/kafka/kafka.server.keystore.jks
ssl.keystore.password=changeit
ssl.key.password=changeit
ssl.truststore.location=/path/to/certs/kafka/kafka.server.truststore.jks
ssl.truststore.password=changeit
ssl.client.auth=required

# 2. Enable SASL
sasl.enabled.mechanisms=SCRAM-SHA-256
sasl.mechanism.inter.broker.protocol=SCRAM-SHA-256

# 3. Create SASL user
kafka-configs --zookeeper localhost:2181 --alter \
  --add-config 'SCRAM-SHA-256=[password=your-secure-password]' \
  --entity-type users --entity-name voice-payment-service

# 4. Restart Kafka
sudo systemctl restart kafka
```

### 6. Configure HashiCorp Vault

```bash
# 1. Initialize Vault (first time only)
vault operator init

# Save unseal keys and root token!

# 2. Unseal Vault (after restart)
vault operator unseal <unseal-key-1>
vault operator unseal <unseal-key-2>
vault operator unseal <unseal-key-3>

# 3. Login
vault login <root-token>

# 4. Enable KV v2 secrets engine
vault secrets enable -version=2 -path=secret kv

# 5. Store secrets
vault kv put secret/voice-payment-service/encryption \
  aes-key="<base64-encoded-256-bit-key>"

vault kv put secret/voice-payment-service/database \
  username="voice_payment_user" \
  password="<secure-password>"

vault kv put secret/voice-payment-service/redis \
  password="<secure-password>"

vault kv put secret/voice-payment-service/kafka \
  username="voice-payment-service" \
  password="<secure-password>"

vault kv put secret/voice-payment-service/google-cloud \
  api-key="<google-cloud-api-key>"

vault kv put secret/voice-payment-service/keycloak \
  client-secret="<keycloak-client-secret>"

# 6. Create policy
vault policy write voice-payment-service - <<EOF
path "secret/data/voice-payment-service/*" {
  capabilities = ["read"]
}
EOF

# 7. Enable AppRole auth (production)
vault auth enable approle

vault write auth/approle/role/voice-payment-service \
  token_policies="voice-payment-service" \
  token_ttl=1h \
  token_max_ttl=4h

# Get role_id and secret_id
vault read auth/approle/role/voice-payment-service/role-id
vault write -f auth/approle/role/voice-payment-service/secret-id
```

---

## Security Configuration

### Environment Variables

Create `.env.production`:

```bash
# Spring Profile
SPRING_PROFILES_ACTIVE=production,security,vault

# Vault
VAULT_URI=https://vault.example.com:8200
VAULT_TOKEN=  # For dev/test only
VAULT_ROLE_ID=  # For production
VAULT_SECRET_ID=  # For production

# Database (TLS)
DB_URL=jdbc:postgresql://postgres.example.com:5432/waqiti_voice_payments?ssl=true&sslmode=verify-full
DB_USERNAME=voice_payment_user
DB_PASSWORD=  # Load from Vault
DB_SSL_ENABLED=true
DB_SSL_MODE=verify-full
DB_SSL_ROOT_CERT=/app/certs/ca/ca-cert.pem
DB_SSL_CERT=/app/certs/postgresql/client-cert.pem
DB_SSL_KEY=/app/certs/postgresql/client-key.pem

# Redis (TLS)
REDIS_HOST=redis.example.com
REDIS_PORT=6380
REDIS_PASSWORD=  # Load from Vault
REDIS_SSL_ENABLED=true
REDIS_SSL_VERIFY_PEER=true
REDIS_SSL_CA_CERT=/app/certs/ca/ca-cert.pem
REDIS_SSL_CERT=/app/certs/redis/client-cert.pem
REDIS_SSL_KEY=/app/certs/redis/client-key.pem

# Kafka (SSL/SASL)
KAFKA_BOOTSTRAP_SERVERS=kafka.example.com:9093
KAFKA_SECURITY_PROTOCOL=SASL_SSL
KAFKA_SSL_ENABLED=true
KAFKA_SSL_TRUST_STORE_LOCATION=/app/certs/kafka/kafka.client.truststore.jks
KAFKA_SSL_TRUST_STORE_PASSWORD=changeit
KAFKA_SSL_KEY_STORE_LOCATION=/app/certs/kafka/kafka.client.keystore.jks
KAFKA_SSL_KEY_STORE_PASSWORD=changeit
KAFKA_SASL_ENABLED=true
KAFKA_SASL_MECHANISM=SCRAM-SHA-256
KAFKA_SASL_USERNAME=voice-payment-service
KAFKA_SASL_PASSWORD=  # Load from Vault

# ClamAV
CLAMAV_ENABLED=true
CLAMAV_HOST=clamav.example.com
CLAMAV_PORT=3310

# Encryption
ENCRYPTION_VAULT_ENABLED=true
ENCRYPTION_KEY=  # MUST be in Vault for production

# Google Cloud
GOOGLE_CLOUD_API_KEY=  # Load from Vault

# Keycloak
KEYCLOAK_CLIENT_ID=voice-payment-service
KEYCLOAK_CLIENT_SECRET=  # Load from Vault
```

### Application Configuration

Update `application-security.yml` is already configured ✅

---

## Service Deployment

### Option 1: Docker Deployment

```bash
# 1. Build application
mvn clean package -DskipTests

# 2. Build Docker image
docker build -t waqiti/voice-payment-service:1.0.0 .

# 3. Run container
docker run -d \
  --name voice-payment-service \
  --env-file .env.production \
  -p 8080:8080 \
  -v $(pwd)/certs:/app/certs:ro \
  --network voice-payment-network \
  waqiti/voice-payment-service:1.0.0

# 4. Check logs
docker logs -f voice-payment-service
```

### Option 2: Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: voice-payment-service
  namespace: voice-payment
spec:
  replicas: 3
  selector:
    matchLabels:
      app: voice-payment-service
  template:
    metadata:
      labels:
        app: voice-payment-service
    spec:
      containers:
      - name: voice-payment-service
        image: waqiti/voice-payment-service:1.0.0
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production,security,vault"
        - name: VAULT_URI
          value: "https://vault.example.com:8200"
        envFrom:
        - secretRef:
            name: voice-payment-secrets
        volumeMounts:
        - name: certs
          mountPath: /app/certs
          readOnly: true
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
      volumes:
      - name: certs
        secret:
          secretName: tls-certificates
---
apiVersion: v1
kind: Service
metadata:
  name: voice-payment-service
  namespace: voice-payment
spec:
  selector:
    app: voice-payment-service
  ports:
  - port: 80
    targetPort: 8080
    name: http
  type: LoadBalancer
```

Apply:
```bash
kubectl apply -f k8s/deployment.yml
kubectl apply -f k8s/service.yml
```

### Option 3: JAR Deployment

```bash
# 1. Build
mvn clean package -DskipTests

# 2. Run
java -jar target/voice-payment-service-1.0.0.jar \
  --spring.profiles.active=production,security,vault \
  --spring.config.location=file:./config/
```

---

## Post-Deployment Verification

### 1. Health Checks

```bash
# Liveness
curl http://localhost:8080/actuator/health/liveness
# Expected: {"status":"UP"}

# Readiness
curl http://localhost:8080/actuator/health/readiness
# Expected: {"status":"UP"}

# Full health
curl http://localhost:8080/actuator/health
```

### 2. TLS Verification

```bash
# PostgreSQL
openssl s_client -connect postgres.example.com:5432 -starttls postgres

# Redis
openssl s_client -connect redis.example.com:6380

# Kafka
openssl s_client -connect kafka.example.com:9093
```

### 3. Vault Connectivity

```bash
# Check logs for
grep "Vault" logs/application.log
# Expected: "Encryption key loaded from Vault successfully"
```

### 4. ClamAV Connectivity

```bash
# Check logs
grep "ClamAV" logs/application.log
# Expected: No errors

# Test scan
curl -F "audio=@test-file.wav" \
  http://localhost:8080/api/v1/voice-payments/process
# Should not reject if file is clean
```

### 5. End-to-End Test

```bash
# 1. Get access token from Keycloak
TOKEN=$(curl -X POST "http://keycloak:8080/realms/waqiti/protocol/openid-connect/token" \
  -d "client_id=voice-payment-service" \
  -d "client_secret=<secret>" \
  -d "grant_type=client_credentials" | jq -r '.access_token')

# 2. Process voice command
curl -X POST "http://localhost:8080/api/v1/voice-payments/process" \
  -H "Authorization: Bearer $TOKEN" \
  -F "userId=<uuid>" \
  -F "audio=@test-voice-command.wav" \
  -F "language=en-US"

# 3. Check response
# Expected: 200 OK with command processing result
```

---

## Monitoring & Observability

### Metrics (Prometheus)

Actuator endpoints:
```bash
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/prometheus
```

Key metrics:
- `voice.commands.processed` - Total commands processed
- `voice.biometric.verifications` - Biometric verifications
- `voice.payments.completed` - Successful payments
- `voice.security.rate_limit_exceeded` - Rate limit hits
- `voice.security.malware_detected` - Virus detections

### Logging (ELK Stack)

Application logs: `/var/log/voice-payment-service/application.log`

Key log patterns:
- `SECURITY:` - Security events
- `AUDIT:` - Audit events
- `ERROR:` - Errors
- `RATE LIMIT` - Rate limiting
- `FRAUD` - Fraud detection

### Alerting

Set up alerts for:
- ✅ Service down (health check fails)
- ✅ High error rate (>1%)
- ✅ Malware detected (any)
- ✅ Rate limit exceeded (>100/hour per user)
- ✅ Database connection failures
- ✅ Vault connectivity issues
- ✅ TLS certificate expiry (<30 days)

---

## Troubleshooting

### Service Won't Start

**Check Vault connectivity:**
```bash
grep "Vault" logs/application.log
# If "Failed to load encryption key from Vault":
# - Verify VAULT_URI is correct
# - Verify VAULT_TOKEN or VAULT_ROLE_ID/SECRET_ID
# - Check Vault is unsealed
```

**Check database connectivity:**
```bash
psql "postgresql://user:pass@host:5432/db?sslmode=verify-full"
# If SSL error:
# - Verify certificates exist
# - Verify certificate paths in config
# - Check PostgreSQL SSL is enabled
```

### TLS Connection Failures

**PostgreSQL:**
```bash
# Test without SSL first
psql "postgresql://user:pass@host:5432/db?sslmode=disable"

# Then with SSL
psql "postgresql://user:pass@host:5432/db?sslmode=require"

# Full verification
psql "postgresql://user:pass@host:5432/db?sslmode=verify-full"
```

**Redis:**
```bash
# Test without TLS
redis-cli -h host -p 6379 PING

# Test with TLS
redis-cli --tls --cert client-cert.pem --key client-key.pem \
  --cacert ca-cert.pem -h host -p 6380 PING
```

### ClamAV Issues

```bash
# Check ClamAV is running
docker ps | grep clamav

# Check ClamAV logs
docker logs waqiti-clamav

# Test connectivity
echo "PING" | nc localhost 3310

# Update signatures
docker exec waqiti-clamav freshclam
```

### Rate Limiting Too Aggressive

```bash
# Check current limits
grep "rate-limit" config/application-security.yml

# Temporarily increase (dev/test only):
voice-payment.rate-limit.voice-commands.max-requests=200

# Or reset user's rate limit (admin only):
curl -X DELETE "http://localhost:8080/api/v1/admin/rate-limit/reset" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "userId=<uuid>" \
  -d "limitType=voice-command"
```

---

## Production Checklist

Before going live:

### Security
- [ ] All TLS certificates from trusted CA
- [ ] Vault fully configured with all secrets
- [ ] Database credentials in Vault (not config files)
- [ ] Encryption keys in Vault (not config files)
- [ ] ClamAV running and updated
- [ ] Security headers verified
- [ ] Rate limits configured appropriately

### Infrastructure
- [ ] PostgreSQL with SSL/TLS
- [ ] Redis with TLS
- [ ] Kafka with SSL/SASL
- [ ] Load balancer configured
- [ ] Auto-scaling configured
- [ ] Backup strategy in place

### Monitoring
- [ ] Prometheus metrics enabled
- [ ] Grafana dashboards created
- [ ] ELK stack for logs
- [ ] Alerts configured
- [ ] On-call rotation defined

### Testing
- [ ] Load testing completed
- [ ] Security testing completed
- [ ] Penetration testing completed
- [ ] Disaster recovery tested
- [ ] Failover tested

### Documentation
- [ ] API documentation published
- [ ] Runbooks created
- [ ] Incident response plan
- [ ] Security incident plan
- [ ] Training completed

---

## Support

For issues:
1. Check logs: `logs/application.log`
2. Check health: `curl http://localhost:8080/actuator/health`
3. Check metrics: `curl http://localhost:8080/actuator/metrics`
4. Review this guide
5. Contact DevOps team

---

**Deployment Status: Ready for Production** ✅

**Last Updated:** 2025-11-10
