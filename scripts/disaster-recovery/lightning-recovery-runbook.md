# Lightning Network Disaster Recovery Runbook

## Overview
This runbook provides step-by-step procedures for Lightning Network disaster recovery scenarios. Follow these procedures carefully to minimize downtime and ensure data integrity.

## Recovery Time Objectives (RTO) and Recovery Point Objectives (RPO)
- **RTO**: 30 minutes maximum downtime
- **RPO**: 5 minutes maximum data loss
- **Backup Frequency**: Channels (1 hour), Database (6 hours), Full backup (6 hours)

## Pre-Disaster Preparation

### 1. Backup Verification
```bash
# Check backup system health
curl -X GET "http://localhost:8080/api/v1/lightning/backup/status" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# List available backups
curl -X GET "http://localhost:8080/api/v1/lightning/backup/list" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### 2. Required Access
- Admin access to Waqiti platform
- Root access to Bitcoin/Lightning servers
- Access to backup storage (local and remote)
- Access to monitoring systems

### 3. Contact Information
- DevOps Team: devops@example.com
- Security Team: security@example.com
- Management: management@example.com

## Disaster Scenarios and Recovery Procedures

### Scenario 1: Lightning Service Crash/Restart

**Symptoms:**
- Lightning endpoints returning 503 errors
- Health checks failing
- No response from LND gRPC

**Recovery Steps:**
1. **Check LND Status** (2 minutes)
```bash
docker logs lnd --tail 100
docker-compose ps lnd
```

2. **Restart Lightning Service** (3 minutes)
```bash
docker-compose restart lnd
# Wait for sync
docker logs lnd -f | grep "Waiting for wallet encryption password"
```

3. **Unlock Wallet** (1 minute)
```bash
# Use wallet unlock endpoint
curl -X POST "http://localhost:8080/api/v1/lightning/wallet/unlock" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"password": "$LND_WALLET_PASSWORD"}'
```

4. **Verify Recovery** (2 minutes)
```bash
# Check node info
curl -X GET "http://localhost:8080/api/v1/lightning/node/info" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Check channels
curl -X GET "http://localhost:8080/api/v1/lightning/channels" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

**Expected Recovery Time: 8 minutes**

### Scenario 2: Database Corruption/Loss

**Symptoms:**
- Database connection errors
- Data inconsistencies
- Application startup failures

**Recovery Steps:**
1. **Stop All Services** (2 minutes)
```bash
docker-compose down
```

2. **Identify Latest Database Backup** (1 minute)
```bash
curl -X GET "http://localhost:8080/api/v1/lightning/backup/list" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.[0]'
```

3. **Execute Database Recovery** (10 minutes)
```bash
# Restore from backup
curl -X POST "http://localhost:8080/api/v1/lightning/backup/disaster-recovery" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"backupPath": "/var/backups/lightning/latest-db-backup"}'
```

4. **Restart Services** (5 minutes)
```bash
docker-compose up -d
```

5. **Verify Data Integrity** (5 minutes)
```bash
# Check invoice data
curl -X GET "http://localhost:8080/api/v1/lightning/invoices" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Check payment history
curl -X GET "http://localhost:8080/api/v1/lightning/payments" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

**Expected Recovery Time: 23 minutes**

### Scenario 3: Channel Data Loss

**Symptoms:**
- Channels showing as closed
- Unable to route payments
- Channel balance mismatches

**Recovery Steps:**
1. **Stop LND Service** (1 minute)
```bash
docker-compose stop lnd
```

2. **Restore Channel Backup** (5 minutes)
```bash
# Copy latest channel backup
cp /var/backups/lightning/channels/latest-backup.scb /data/lnd/data/chain/bitcoin/mainnet/channel.backup

# Restore using LND
docker-compose up -d lnd
docker exec -it lnd lncli restorechanbackup --multi_file /data/channel.backup
```

3. **Wait for Channel Recovery** (10 minutes)
```bash
# Monitor channel recovery
docker logs lnd -f | grep -i channel
```

4. **Verify Channel States** (3 minutes)
```bash
curl -X GET "http://localhost:8080/api/v1/lightning/channels" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

**Expected Recovery Time: 19 minutes**

### Scenario 4: Complete System Loss

**Symptoms:**
- Hardware failure
- Data center outage
- Complete system unavailability

**Recovery Steps:**
1. **Prepare New Infrastructure** (60 minutes - parallel with other steps)
```bash
# Deploy new servers
# Install Docker and dependencies
# Set up networking and security
```

2. **Retrieve Latest Full Backup** (5 minutes)
```bash
# Download from remote storage
aws s3 sync s3://waqiti-lightning-backups/latest ./recovery/
```

3. **Execute Full Disaster Recovery** (20 minutes)
```bash
# Extract backup
tar -xzf lightning-backup-latest.tar.gz -C /recovery/

# Execute recovery
curl -X POST "http://localhost:8080/api/v1/lightning/backup/disaster-recovery" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"backupPath": "/recovery/lightning-backup-latest"}'
```

4. **Comprehensive Verification** (10 minutes)
```bash
# Verify all components
./scripts/verify-recovery.sh
```

**Expected Recovery Time: 95 minutes**

### Scenario 5: Wallet Corruption

**Symptoms:**
- Wallet unlock failures
- Invalid wallet state
- Cannot access funds

**Recovery Steps:**
1. **Stop LND** (1 minute)
```bash
docker-compose stop lnd
```

2. **Backup Current State** (2 minutes)
```bash
cp -r /data/lnd /data/lnd-backup-$(date +%s)
```

3. **Restore Wallet from Seed** (10 minutes)
```bash
# Remove current wallet
rm -rf /data/lnd/data/chain/bitcoin/mainnet/wallet.db

# Start LND
docker-compose up -d lnd

# Restore from seed
docker exec -it lnd lncli create --recover
# Enter seed phrase when prompted
```

4. **Restore Channels** (5 minutes)
```bash
# Restore channel backup
docker exec -it lnd lncli restorechanbackup --multi_file /data/channel.backup
```

5. **Verify Recovery** (5 minutes)
```bash
curl -X GET "http://localhost:8080/api/v1/lightning/wallet/balance" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

**Expected Recovery Time: 23 minutes**

## Post-Recovery Procedures

### 1. Verify System Health
```bash
# Check all health endpoints
curl -X GET "http://localhost:8080/api/v1/lightning/health"
curl -X GET "http://localhost:8080/api/v1/lightning/backup/status"

# Verify Bitcoin node sync
curl -X GET "http://localhost:8080/api/v1/bitcoin/health"
```

### 2. Resume Backup Schedule
```bash
# Trigger immediate backup
curl -X POST "http://localhost:8080/api/v1/lightning/backup/full" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### 3. Test Critical Functions
```bash
# Test invoice creation
curl -X POST "http://localhost:8080/api/v1/lightning/invoices" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"amount_sat": 1000, "description": "Recovery test"}'

# Test payment (small amount)
curl -X POST "http://localhost:8080/api/v1/lightning/payments" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"payment_request": "$TEST_INVOICE"}'
```

### 4. Update Documentation
- Document the incident
- Update recovery procedures if needed
- Schedule post-mortem review

## Monitoring and Alerting

### Key Metrics to Monitor Post-Recovery
- Channel balance and capacity
- Payment success rate
- Invoice settlement rate
- Node synchronization status
- Backup completion status

### Alert Thresholds
- Channel balance < 10% of capacity
- Payment failure rate > 5%
- Invoice settlement time > 30 seconds
- Node sync lag > 10 blocks
- Backup failure

## Testing and Validation

### Monthly DR Tests
1. **Backup Restoration Test**
   - Restore to test environment
   - Verify data integrity
   - Test all functionalities

2. **Failover Test**
   - Simulate service failures
   - Test recovery procedures
   - Measure recovery times

3. **End-to-End Test**
   - Test complete user workflows
   - Verify payment processing
   - Check audit trails

### Annual DR Exercises
- Complete disaster scenario simulation
- Cross-team coordination exercise
- Recovery time optimization
- Documentation updates

## Common Issues and Solutions

### Issue: Channel Recovery Takes Too Long
**Solution:**
- Check network connectivity
- Verify peer connections
- Consider force-closing stuck channels

### Issue: Database Restore Fails
**Solution:**
- Check backup file integrity
- Verify disk space
- Try alternative backup if available

### Issue: Wallet Unlock Fails
**Solution:**
- Verify password
- Check wallet file permissions
- Consider wallet restoration from seed

### Issue: Payment Routes Not Found
**Solution:**
- Wait for channel graph sync
- Reconnect to key routing peers
- Check channel liquidity

## Emergency Contacts
- **Primary On-Call**: +1-XXX-XXX-XXXX
- **Secondary On-Call**: +1-XXX-XXX-XXXX
- **Management**: +1-XXX-XXX-XXXX

## Documentation Updates
- **Last Updated**: 2024-12-09
- **Next Review**: 2024-01-09
- **Version**: 1.0

---

**IMPORTANT**: Always follow this runbook in sequence. Document any deviations and their outcomes for future improvements.