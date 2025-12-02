# Service Integration with HashiCorp Vault

This directory contains templates and scripts to integrate Waqiti microservices with HashiCorp Vault.

## Quick Start

### 1. Deploy Vault (if not already done)

```bash
cd ../
./deploy-vault.sh
```

### 2. Create Service Tokens

```bash
cd service-integration/
chmod +x create-service-tokens.sh
./create-service-tokens.sh
```

This creates:
- Vault policies for each service
- Service tokens (30-day renewable)
- Kubernetes secrets containing tokens

### 3. Update Service Configuration

For each service, update `application.yml`:

```yaml
spring:
  cloud:
    vault:
      enabled: true
      host: vault.vault-system.svc.cluster.local
      port: 8200
      scheme: https
      authentication: TOKEN
      token: ${VAULT_TOKEN}
      kv:
        enabled: true
        backend: secret
        default-context: waqiti
        application-name: payment-service  # Change per service
```

See `application-vault-template.yml` for complete configuration.

### 4. Update Kubernetes Deployments

Patch each deployment to inject `VAULT_TOKEN`:

```yaml
env:
  - name: VAULT_TOKEN
    valueFrom:
      secretKeyRef:
        name: payment-service-vault-token
        key: vault-token
```

See `deployment-patch-example.yaml` for complete examples.

### 5. Verify Integration

```bash
# Check if service can read secrets
kubectl exec -it <pod-name> -- curl -s localhost:8080/actuator/health | jq '.components.vault'

# Expected output:
# {
#   "status": "UP",
#   "details": {
#     "version": "1.15.4"
#   }
# }
```

## Files

| File | Description |
|------|-------------|
| `application-vault-template.yml` | Complete Vault configuration template |
| `VaultConfigurationExample.java` | Java configuration class with examples |
| `create-service-tokens.sh` | Script to create tokens for all services |
| `deployment-patch-example.yaml` | Kubernetes deployment patches |

## Service-Specific Secrets

Each service has access to:

### Common Secrets (All Services)
- `secret/data/waqiti/jwt-secret` - JWT signing key
- `secret/data/waqiti/jwt-refresh-secret` - JWT refresh key
- `secret/data/waqiti/encryption/field-encryption-key` - PII encryption key

### Service-Specific Secrets
- `secret/data/waqiti/{service}/database` - Database credentials
- `secret/data/waqiti/{service}/redis` - Redis password
- `secret/data/waqiti/{service}/kafka` - Kafka SASL credentials
- `secret/data/waqiti/{service}/external-apis` - Third-party API keys

### Payment Services
- `secret/data/waqiti/payment-service/stripe`
- `secret/data/waqiti/payment-service/paypal`
- `secret/data/waqiti/payment-service/flutterwave`

### Notification Services
- `secret/data/waqiti/notification-service/twilio`
- `secret/data/waqiti/notification-service/sendgrid`

### KYC Service
- `secret/data/waqiti/kyc-service/sumsub`
- `secret/data/waqiti/kyc-service/onfido`

### Integration Services
- `secret/data/waqiti/integration-service/plaid`
- `secret/data/waqiti/integration-service/finicity`

## Usage Examples

### Reading Secrets in Java

#### Option 1: Spring @Value (Recommended)

```java
@Service
public class PaymentService {

    @Value("${vault.stripe.api-key}")
    private String stripeApiKey;

    @Value("${vault.database.password}")
    private String dbPassword;
}
```

#### Option 2: Programmatic Access

```java
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final VaultSecretService vaultSecretService;

    public void processPayment() {
        String stripeKey = vaultSecretService.readSecret(
            "secret/data/waqiti/payment-service/stripe",
            "api_key"
        );

        // Use the key
    }
}
```

#### Option 3: Using Common SecretService

Most services already use `SecretService` from common module:

```java
@Service
@RequiredArgsConstructor
public class JwtService {

    private final SecretService secretService;  // From common module

    public String generateToken(String userId) {
        String jwtSecret = secretService.getJwtSecret();
        // Generate token
    }
}
```

## Updating Secrets

### Via Vault CLI

```bash
# Login to Vault
export VAULT_ADDR="https://vault.example.com:8200"
vault login <root-token>

# Update a secret
vault kv put secret/waqiti/payment-service/stripe \
  api_key="sk_live_..." \
  webhook_secret="whsec_..."

# Read a secret
vault kv get secret/waqiti/payment-service/stripe
```

### Via Vault UI

1. Navigate to https://vault.example.com:8200
2. Login with token
3. Navigate to Secrets > secret > waqiti > [service-name]
4. Click "Create secret" or edit existing

### Via Kubernetes

```bash
# Port-forward to Vault
kubectl port-forward -n vault-system svc/vault 8200:8200

# Access locally
export VAULT_ADDR="https://localhost:8200"
vault login <token>
vault kv put secret/waqiti/payment-service/stripe api_key="..."
```

## Token Rotation

Service tokens are created with 30-day period and auto-renew. To manually rotate:

```bash
# Revoke old token
vault token revoke <old-token>

# Create new token
./create-service-tokens.sh

# Update Kubernetes secret
kubectl delete secret payment-service-vault-token -n waqiti-services
kubectl create secret generic payment-service-vault-token \
  --namespace=waqiti-services \
  --from-literal=vault-token="<new-token>"

# Restart service
kubectl rollout restart deployment/payment-service -n waqiti-services
```

## Troubleshooting

### Service can't connect to Vault

```bash
# Check Vault is running
kubectl get pods -n vault-system

# Check service can resolve vault DNS
kubectl exec -it <pod> -- nslookup vault.vault-system.svc.cluster.local

# Check token is valid
kubectl exec -it <pod> -- env | grep VAULT_TOKEN
```

### Secret not found

```bash
# List all secrets
vault kv list secret/waqiti/

# Check path exists
vault kv get secret/waqiti/payment-service/stripe

# Check service policy
vault policy read payment-service-policy
```

### Token expired

```bash
# Check token status
vault token lookup <token>

# Renew token
vault token renew <token>

# Or create new token
./create-service-tokens.sh
```

## Security Best Practices

1. **Never hardcode secrets** - Always use Vault
2. **Use service-specific tokens** - Don't share tokens between services
3. **Rotate tokens regularly** - At least every 90 days
4. **Monitor token usage** - Check Vault audit logs
5. **Limit token TTL** - Use shortest practical TTL
6. **Enable TLS** - Always use HTTPS for Vault communication
7. **Audit access** - Review who accessed what secrets

## Migration Checklist

For each service:

- [ ] Update `application.yml` with Vault configuration
- [ ] Create Vault policy and token
- [ ] Create Kubernetes secret
- [ ] Update deployment YAML to inject token
- [ ] Remove hardcoded secrets from config files
- [ ] Store secrets in Vault
- [ ] Test service startup and secret access
- [ ] Update CI/CD pipeline to use Vault
- [ ] Update documentation
- [ ] Train team on Vault usage

## Next Steps

1. ✅ Deploy Vault cluster
2. ✅ Create service tokens
3. ⏳ Update service configurations (98 services)
4. ⏳ Migrate secrets from config files to Vault
5. ⏳ Update deployment manifests
6. ⏳ Test in staging
7. ⏳ Deploy to production
