# cert-manager Setup Instructions

## Prerequisites

- Kubernetes cluster (1.23+)
- kubectl configured
- Cloudflare or Route53 DNS access
- Cloudflare API token or AWS IAM role

## Installation

### Step 1: Install cert-manager

```bash
# Install cert-manager CRDs and controller
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml

# Verify installation
kubectl get pods --namespace cert-manager

# Expected output:
# NAME                                      READY   STATUS    RESTARTS   AGE
# cert-manager-xxxxxxx-xxxxx                1/1     Running   0          1m
# cert-manager-cainjector-xxxxxxx-xxxxx     1/1     Running   0          1m
# cert-manager-webhook-xxxxxxx-xxxxx        1/1     Running   0          1m
```

### Step 2: Create Cloudflare API Token Secret

```bash
# Create Cloudflare API token with DNS edit permissions
# Go to: https://dash.cloudflare.com/profile/api-tokens
# Permissions: Zone - DNS - Edit
# Zone Resources: Include - Specific zone - example.com

# Create secret
kubectl create secret generic cloudflare-api-token \
  --from-literal=api-token=YOUR_CLOUDFLARE_API_TOKEN \
  --namespace=cert-manager
```

### Step 3: Apply ClusterIssuer

```bash
# Apply cert-manager configuration
kubectl apply -f kubernetes/cert-manager-setup.yaml

# Verify ClusterIssuer
kubectl get clusterissuer
kubectl describe clusterissuer letsencrypt-prod
```

### Step 4: Create Certificate

```bash
# Certificate will be created automatically from cert-manager-setup.yaml
# Or create manually:
kubectl apply -f - <<EOF
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: waqiti-wildcard-tls
  namespace: waqiti-production
spec:
  secretName: waqiti-tls-secret
  issuerRef:
    name: letsencrypt-prod
    kind: ClusterIssuer
  dnsNames:
  - example.com
  - '*.example.com'
EOF

# Monitor certificate issuance
kubectl get certificate -n waqiti-production
kubectl describe certificate waqiti-wildcard-tls -n waqiti-production

# Watch events
kubectl get events -n waqiti-production --sort-by='.lastTimestamp'
```

### Step 5: Verify Certificate

```bash
# Check certificate secret
kubectl get secret waqiti-tls-secret -n waqiti-production

# Inspect certificate
kubectl get secret waqiti-tls-secret -n waqiti-production -o jsonpath='{.data.tls\.crt}' | base64 -d | openssl x509 -noout -text
```

## Troubleshooting

### Certificate not issuing

```bash
# Check certificate status
kubectl describe certificate waqiti-wildcard-tls -n waqiti-production

# Check certificate request
kubectl get certificaterequest -n waqiti-production
kubectl describe certificaterequest <name> -n waqiti-production

# Check order
kubectl get order -n waqiti-production
kubectl describe order <name> -n waqiti-production

# Check challenge
kubectl get challenge -n waqiti-production
kubectl describe challenge <name> -n waqiti-production

# Check cert-manager logs
kubectl logs -n cert-manager -l app=cert-manager -f
```

### DNS validation failing

```bash
# Verify Cloudflare API token
kubectl get secret cloudflare-api-token -n cert-manager -o jsonpath='{.data.api-token}' | base64 -d

# Check DNS TXT record
dig _acme-challenge.example.com TXT

# Test Cloudflare API access
curl -X GET "https://api.cloudflare.com/client/v4/zones" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json"
```

## Renewal

Certificates automatically renew 30 days before expiration.

```bash
# Force renewal test
kubectl delete certificate waqiti-wildcard-tls -n waqiti-production
kubectl apply -f kubernetes/cert-manager-setup.yaml

# Monitor renewal
kubectl get certificate -n waqiti-production -w
```

## Monitoring

```bash
# Certificate expiry metrics
kubectl port-forward -n cert-manager svc/cert-manager 9402:9402
curl localhost:9402/metrics | grep certmanager_certificate_expiration

# Grafana dashboard
# Import dashboard ID: 11001 (cert-manager)
```