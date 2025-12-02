# Payroll Service

Enterprise-grade payroll processing microservice for the Waqiti platform.

## Overview

The Payroll Service handles comprehensive payroll processing including:
- Employee salary calculations
- Tax withholding (Federal, State, FICA, Medicare)
- Deductions (401k, health insurance, garnishments)
- Regulatory compliance (FLSA, Equal Pay Act, AML)
- Bank transfers (ACH, Wire)
- Tax reporting (W-2, 1099, Form 941)

## Architecture

### Technology Stack
- **Language**: Java 21
- **Framework**: Spring Boot 3.3.5
- **Database**: PostgreSQL 15+
- **Message Broker**: Apache Kafka
- **Service Discovery**: Eureka
- **Security**: OAuth2/JWT (Keycloak)

### Key Components

#### Services (9 Total)
1. **TaxCalculationService** - IRS-compliant tax calculations
2. **DeductionService** - Pre-tax and post-tax deductions
3. **BankTransferService** - ACH/Wire transfers (NACHA compliant)
4. **ComplianceService** - FLSA, Equal Pay Act, AML screening
5. **ValidationService** - SSN, bank account, employee data validation
6. **NotificationService** - Email/SMS/Push notifications
7. **AuditService** - SOX/GDPR audit trail
8. **ReportingService** - W-2, 1099, Form 941 generation
9. **PayrollProcessingService** - API orchestration

#### Domain Models
- **PayrollBatch** - Batch metadata
- **PayrollPayment** - Individual employee payments
- **Enums**: BatchStatus (9 states), PaymentStatus (10 states), PayrollType (12 types)

#### Repositories
- **PayrollBatchRepository** - 50+ query methods
- **PayrollPaymentRepository** - 40+ query methods

## Quick Start

### Prerequisites
- Java 21+
- PostgreSQL 15+
- Apache Kafka 3.0+
- Maven 3.8+

### Local Development

1. **Clone the repository**
```bash
git clone https://github.com/waqiti/waqiti-app.git
cd waqiti-app/services/payroll-service
```

2. **Configure application.yml**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/waqiti_payroll
    username: waqiti
    password: ${DATABASE_PASSWORD}
  kafka:
    bootstrap-servers: localhost:9092
```

3. **Run database migrations**
```bash
mvn flyway:migrate
```

4. **Build the service**
```bash
mvn clean install
```

5. **Run the service**
```bash
mvn spring-boot:run
```

The service will start on `http://localhost:8095`

## API Endpoints

### Payroll Batch Operations

#### Submit Payroll Batch
```http
POST /api/v1/payroll/batches
Authorization: Bearer {jwt_token}
Content-Type: application/json

{
  "companyId": "COMP001",
  "payPeriod": "2025-01-15",
  "payrollType": "REGULAR",
  "employees": [...],
  "totalAmount": 100000.00
}
```

#### Get Batch Status
```http
GET /api/v1/payroll/batches/{batchId}
Authorization: Bearer {jwt_token}
```

#### Retry Failed Batch
```http
POST /api/v1/payroll/batches/{batchId}/retry
Authorization: Bearer {jwt_token}
```

#### Cancel Batch
```http
DELETE /api/v1/payroll/batches/{batchId}?reason=User%20requested
Authorization: Bearer {jwt_token}
```

#### Approve Batch
```http
POST /api/v1/payroll/batches/{batchId}/approve?approvedBy=manager@company.com
Authorization: Bearer {jwt_token}
```

### Tax Reporting

#### Generate W-2
```http
GET /api/v1/payroll/reports/w2/{employeeId}/{year}
Authorization: Bearer {jwt_token}
```

#### Generate 1099-NEC
```http
GET /api/v1/payroll/reports/1099/{contractorId}/{year}
Authorization: Bearer {jwt_token}
```

#### Generate Form 941
```http
GET /api/v1/payroll/reports/941/{companyId}/{year}/{quarter}
Authorization: Bearer {jwt_token}
```

#### Annual Tax Summary
```http
GET /api/v1/payroll/reports/annual/{companyId}/{year}
Authorization: Bearer {jwt_token}
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DATABASE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/waqiti_payroll` |
| `DATABASE_USERNAME` | Database username | `waqiti` |
| `DATABASE_PASSWORD` | Database password | - |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers | `localhost:9092` |
| `EUREKA_SERVER_URL` | Eureka server URL | `http://localhost:8761/eureka/` |
| `KEYCLOAK_AUTH_SERVER_URL` | Keycloak auth server | `https://localhost:8080` |

### Payroll Configuration

```yaml
payroll:
  processing:
    max-employees-per-batch: 10000
    max-individual-payment: 1000000.00
    max-batch-total: 100000000.00
    max-processing-hours: 24
    max-retries: 3

  tax:
    federal-rate: 0.22
    state-rate: 0.05
    fica-rate: 0.0765
    social-security-rate: 0.062
    medicare-rate: 0.0145
    social-security-wage-base: 160200.00

  compliance:
    minimum-wage: 7.25
    overtime-threshold-hours: 40
    enable-flsa-checks: true
    enable-equal-pay-checks: true
    enable-aml-screening: true
    aml-threshold: 10000.00
```

## Kafka Topics

### Consumer Topics
- `payroll-processing-events` - Incoming payroll batch requests

### Producer Topics
- `payroll-processed-events` - Successful payroll completion
- `tax-filing-events` - Tax reporting events
- `compliance-alert-events` - Compliance violations
- `payment-notification-events` - Employee payment notifications
- `audit-events` - Audit trail events
- `notification-events` - General notifications
- `alert-events` - System alerts

### Dead Letter Queue
- `payroll-processing-events-dlq` - Failed events

## Database Schema

### Tables

#### payroll_batches
Main batch tracking table with:
- 20+ fields (amounts, counts, status, metadata)
- 5 indexes (company, status, pay_period, correlation_id)
- Optimistic locking (@Version)
- Audit columns (created_at, updated_at)

#### payroll_payments
Individual payment records with:
- 30+ fields (employee info, pay breakdown, tax withholding, deductions)
- 6 indexes (batch_id, employee_id, pay_period, status)
- Encrypted fields (SSN, bank account)
- Optimistic locking (@Version)

## Deployment

### Kubernetes

1. **Create namespace**
```bash
kubectl create namespace waqiti
```

2. **Apply secrets** (use Sealed Secrets in production)
```bash
kubectl apply -f k8s/production/payroll-service-secrets.yaml
```

3. **Deploy service**
```bash
kubectl apply -f k8s/production/payroll-service-deployment.yaml
```

4. **Verify deployment**
```bash
kubectl get pods -n waqiti -l app=payroll-service
kubectl logs -n waqiti -l app=payroll-service --tail=100
```

### Docker

```bash
# Build image
docker build -t waqiti/payroll-service:latest .

# Run container
docker run -d \
  -p 8095:8095 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/waqiti_payroll \
  -e DATABASE_PASSWORD=secret \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  waqiti/payroll-service:latest
```

## Monitoring

### Health Checks
- **Liveness**: `/actuator/health/liveness`
- **Readiness**: `/actuator/health/readiness`
- **Startup**: `/actuator/health`

### Metrics
- **Prometheus**: `/actuator/prometheus`
- **Metrics**: `/actuator/metrics`

### Key Metrics to Monitor
- `payroll_batches_processed_total` - Total batches processed
- `payroll_batch_processing_time_seconds` - Processing time histogram
- `payroll_batch_success_rate` - Success rate percentage
- `payroll_tax_withholding_total` - Total tax withheld
- `payroll_compliance_violations_total` - Compliance violation count

## Compliance & Security

### Regulatory Compliance
- ✅ **FLSA** - Fair Labor Standards Act (minimum wage, overtime)
- ✅ **FICA** - Federal Insurance Contributions Act (Social Security, Medicare)
- ✅ **FUTA** - Federal Unemployment Tax Act
- ✅ **Equal Pay Act** - Pay equity monitoring
- ✅ **AML** - Anti-Money Laundering screening (>$10k payments)
- ✅ **SOX** - Sarbanes-Oxley audit trail
- ✅ **GDPR** - Data access logging

### Security Features
- Field-level encryption (SSN, bank accounts, salary)
- OAuth2/JWT authentication
- Role-based access control (PAYROLL_SUBMIT, PAYROLL_READ, PAYROLL_APPROVE)
- Audit trail for all operations
- PCI DSS compliant payment data handling

## Tax Forms Supported
- **W-2** - Employee Annual Wage Statement (IRS Box 1-20)
- **1099-NEC** - Non-Employee Compensation
- **Form 941** - Employer's Quarterly Federal Tax Return
- **Annual Tax Summary** - Yearly totals and quarterly breakdown

## Troubleshooting

### Common Issues

**Issue**: Database connection fails
```bash
# Check PostgreSQL is running
psql -h localhost -U waqiti -d waqiti_payroll

# Verify connection string
echo $DATABASE_URL
```

**Issue**: Kafka connection fails
```bash
# Check Kafka brokers
kafka-topics.sh --list --bootstrap-server localhost:9092

# Verify topic exists
kafka-topics.sh --describe --topic payroll-processing-events --bootstrap-server localhost:9092
```

**Issue**: Batch processing stuck
```bash
# Check for stale batches
SELECT * FROM payroll_batches
WHERE status = 'PROCESSING'
AND processing_started_at < NOW() - INTERVAL '1 hour';

# Manually retry
curl -X POST http://localhost:8095/api/v1/payroll/batches/{batchId}/retry
```

## Development

### Running Tests
```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# Coverage report
mvn jacoco:report
```

### Code Quality
```bash
# Format code
mvn spotless:apply

# Run linter
mvn checkstyle:check

# Security scan
mvn dependency-check:check
```

## Contributing

1. Create feature branch: `git checkout -b feature/payroll-enhancement`
2. Make changes and test thoroughly
3. Run code quality checks: `mvn clean verify`
4. Commit with descriptive message
5. Push and create pull request

## Support

- **Documentation**: https://docs.example.com/payroll-service
- **Issues**: https://github.com/waqiti/waqiti-app/issues
- **Slack**: #payroll-service channel

## License

Proprietary - Waqiti Platform © 2025
