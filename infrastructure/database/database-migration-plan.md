# Database Migration Plan for Microservice Isolation

## Current State
- All microservices share a single PostgreSQL database instance
- Services use different schemas but same database connection
- Risk of cross-service data access and tight coupling
- Performance bottlenecks due to shared resources

## Target State
- Each microservice has its own dedicated database instance
- Complete data isolation between services
- Service-specific database optimizations
- Independent scaling and maintenance

## Migration Strategy

### Phase 1: Database Infrastructure Setup (Week 1)
1. **Create Isolated Database Instances**
   - Deploy dedicated PostgreSQL instances for each service
   - Configure network isolation and security
   - Set up monitoring and backup procedures

2. **Service Database Mapping**
   ```
   User Service       -> postgres-user:5433       -> waqiti_users
   Payment Service    -> postgres-payment:5434    -> waqiti_payments
   Wallet Service     -> postgres-wallet:5435     -> waqiti_wallets
   Ledger Service     -> postgres-ledger:5436     -> waqiti_ledger
   Compliance Service -> postgres-compliance:5437 -> waqiti_compliance
   Analytics Service  -> postgres-analytics:5438  -> waqiti_analytics
   Security Service   -> postgres-security:5439   -> waqiti_security
   Notification Service -> postgres-notification:5440 -> waqiti_notifications
   ```

### Phase 2: Data Migration (Week 2)
1. **Schema Export and Migration**
   ```bash
   # Export existing schemas
   pg_dump --schema-only --schema=user_schema waqiti > user_schema.sql
   pg_dump --schema-only --schema=payment_schema waqiti > payment_schema.sql
   pg_dump --schema-only --schema=wallet_schema waqiti > wallet_schema.sql
   
   # Migrate data
   pg_dump --data-only --schema=user_schema waqiti | psql -h postgres-user -U waqiti_user_service waqiti_users
   ```

2. **Data Consistency Verification**
   - Run data integrity checks
   - Verify foreign key relationships are properly handled
   - Ensure no data loss during migration

### Phase 3: Application Configuration Update (Week 3)
1. **Update Service Configurations**
   - Modify application.yml for each service
   - Update database connection strings
   - Configure service-specific database pools

2. **Service-by-Service Deployment**
   - Deploy services one at a time
   - Verify functionality after each deployment
   - Rollback capability maintained

### Phase 4: Testing and Validation (Week 4)
1. **Integration Testing**
   - End-to-end transaction flows
   - Cross-service communication testing
   - Performance testing

2. **Production Readiness**
   - Load testing on isolated databases
   - Backup and recovery testing
   - Monitoring and alerting validation

## Database-Specific Configurations

### User Service Database
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres-user:5433/waqiti_users
    username: waqiti_user_service
    password: ${USER_DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
```

### Payment Service Database
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres-payment:5434/waqiti_payments
    username: waqiti_payment_service
    password: ${PAYMENT_DB_PASSWORD}
    hikari:
      maximum-pool-size: 50  # Higher due to transaction volume
      minimum-idle: 10
```

### Wallet Service Database
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres-wallet:5435/waqiti_wallets
    username: waqiti_wallet_service
    password: ${WALLET_DB_PASSWORD}
    hikari:
      maximum-pool-size: 40
      minimum-idle: 8
```

## Security Considerations

### Network Isolation
- Each database runs on its own isolated network
- Services can only access their designated database
- No cross-service database access possible

### Access Control
- Unique database users for each service
- Principle of least privilege applied
- Regular password rotation implemented

### Audit and Monitoring
- Database-level audit logging enabled
- Performance monitoring per service
- Resource utilization tracking

## Backup Strategy

### Per-Database Backups
```bash
# Automated backup script for each database
pg_dump -h postgres-user -U waqiti_user_service waqiti_users > user_backup_$(date +%Y%m%d).sql
pg_dump -h postgres-payment -U waqiti_payment_service waqiti_payments > payment_backup_$(date +%Y%m%d).sql
# ... for each service
```

### Cross-Database Consistency
- Coordinated backup windows
- Transaction log shipping for point-in-time recovery
- Cross-service data consistency checks

## Performance Optimizations

### Service-Specific Tuning
- **Payment Service**: Optimized for high transaction throughput
- **Analytics Service**: Optimized for complex queries and reporting
- **User Service**: Optimized for read-heavy workloads
- **Wallet Service**: Optimized for balance consistency and locking

### Database Parameters
```postgresql
-- Payment Database (High Concurrency)
shared_buffers = 512MB
max_connections = 200
work_mem = 8MB

-- Analytics Database (Query Optimization)
shared_buffers = 1GB
work_mem = 32MB
random_page_cost = 1.1
```

## Rollback Plan

### Emergency Rollback
1. **Immediate**: Revert service configurations to use shared database
2. **Quick**: Restore from last known good backup
3. **Complete**: Full migration rollback with data consolidation

### Validation Checkpoints
- Each migration phase has go/no-go checkpoints
- Automated health checks before proceeding
- Manual verification of critical functionality

## Environment Variables Update

```bash
# Add to .env.template
USER_DB_PASSWORD=generate_secure_password_here
PAYMENT_DB_PASSWORD=generate_secure_password_here
WALLET_DB_PASSWORD=generate_secure_password_here
LEDGER_DB_PASSWORD=generate_secure_password_here
COMPLIANCE_DB_PASSWORD=generate_secure_password_here
ANALYTICS_DB_PASSWORD=generate_secure_password_here
SECURITY_DB_PASSWORD=generate_secure_password_here
NOTIFICATION_DB_PASSWORD=generate_secure_password_here
```

## Success Metrics

### Technical Metrics
- Zero data loss during migration
- <5% performance degradation during transition
- 100% service availability maintained
- All integration tests passing

### Operational Metrics
- Independent database scaling capability
- Isolated maintenance windows
- Reduced blast radius for database issues
- Improved development team autonomy

## Timeline

| Phase | Duration | Activities | Success Criteria |
|-------|----------|------------|------------------|
| Phase 1 | Week 1 | Infrastructure setup | All databases running and accessible |
| Phase 2 | Week 2 | Data migration | Data integrity verified |
| Phase 3 | Week 3 | Application updates | Services connecting to isolated DBs |
| Phase 4 | Week 4 | Testing & validation | All tests passing, performance acceptable |

## Risk Mitigation

### High Risk Items
1. **Data Loss**: Comprehensive backup strategy and validation
2. **Downtime**: Blue-green deployment with rollback capability
3. **Performance**: Load testing and gradual traffic migration
4. **Complexity**: Detailed runbooks and automation

### Monitoring During Migration
- Real-time database performance metrics
- Application error rate monitoring
- Business transaction success rates
- Data consistency validation checks