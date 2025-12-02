# Expense Service

Production-ready microservice for comprehensive expense tracking, budget management, and financial analytics.

## 🚀 Features

- **Expense Management**: Create, update, approve, and track expenses
- **Budget Monitoring**: Set budgets with automatic alerts and monitoring
- **Receipt Processing**: Upload receipts with OCR extraction
- **Analytics**: Comprehensive spending analytics and insights
- **Auto-Categorization**: ML-powered expense categorization
- **Report Generation**: Generate reports in PDF, Excel, and CSV formats
- **GDPR Compliance**: Data export, deletion, and access endpoints
- **Multi-Currency Support**: Handle expenses in multiple currencies
- **Mileage Calculation**: IRS-compliant mileage expense calculations

## 📋 Prerequisites

- Java 21
- Maven 3.9+
- PostgreSQL 15+
- Apache Kafka 3.9+
- Redis 7+

## 🏗️ Architecture

### Technology Stack

- **Framework**: Spring Boot 3.3.4
- **Language**: Java 21
- **Database**: PostgreSQL 15
- **Messaging**: Apache Kafka
- **Cache**: Redis
- **Security**: Keycloak OAuth2/OIDC
- **Monitoring**: Prometheus + Grafana
- **Tracing**: OpenTelemetry

### Key Components

1. **ExpenseService**: Core expense CRUD operations
2. **BudgetService**: Budget management and monitoring
3. **ExpenseReportService**: Report generation (PDF, Excel, CSV)
4. **ExpenseAnalyticsService**: Analytics and insights
5. **ExpenseClassificationService**: ML-powered categorization
6. **NotificationService**: Multi-channel notifications (email, SMS, push)

## 🚦 Getting Started

### Local Development

```bash
# Clone repository
git clone https://github.com/waqiti/waqiti-app.git
cd waqiti-app/services/expense-service

# Build
mvn clean package

# Run
mvn spring-boot:run
```

### Docker

```bash
# Build image
docker build -t waqiti/expense-service:latest .

# Run container
docker run -p 8055:8055 \
  -e SPRING_PROFILES_ACTIVE=production \
  -e EXPENSE_DB_PASSWORD=secret \
  waqiti/expense-service:latest
```

### Kubernetes

```bash
# Apply configurations
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/deployment.yaml

# Check status
kubectl get pods -n waqiti -l app=expense-service
```

## 📡 API Endpoints

### Expense Management

- `POST /api/v1/expenses` - Create expense
- `GET /api/v1/expenses/{id}` - Get expense by ID
- `PUT /api/v1/expenses/{id}` - Update expense
- `DELETE /api/v1/expenses/{id}` - Delete expense
- `POST /api/v1/expenses/{id}/receipt` - Upload receipt
- `POST /api/v1/expenses/{id}/approve` - Approve/reject expense

### Budget Management

- `POST /api/v1/budgets` - Create budget
- `GET /api/v1/budgets/{id}` - Get budget by ID
- `PUT /api/v1/budgets/{id}` - Update budget
- `GET /api/v1/budgets/user/{userId}` - Get user budgets

### Analytics

- `GET /api/v1/expenses/summary` - Get expense summary
- `GET /api/v1/expenses/analytics` - Get detailed analytics
- `POST /api/v1/expenses/reports/generate` - Generate report

### GDPR Compliance

- `GET /api/v1/gdpr/export` - Export user data
- `DELETE /api/v1/gdpr/delete-data` - Delete user data
- `GET /api/v1/gdpr/data-summary` - Get data summary

## 🔒 Security

- **Authentication**: OAuth 2.0 / JWT via Keycloak
- **Authorization**: Role-based access control (RBAC)
- **Data Encryption**: At-rest and in-transit encryption
- **Rate Limiting**: Per-user and per-endpoint rate limits
- **Circuit Breakers**: Resilience4j for fault tolerance

## 📊 Monitoring

### Health Checks

- **Liveness**: `GET /actuator/health/liveness`
- **Readiness**: `GET /actuator/health/readiness`

### Metrics

- **Prometheus**: `GET /actuator/prometheus`
- **Custom Metrics**:
  - `expense.created` - Expense creation count
  - `expense.approved` - Expense approval count
  - `budget.exceeded` - Budget exceeded count
  - `expense.amount` - Expense amount distribution

### Tracing

Distributed tracing via OpenTelemetry with 10% sampling rate.

## 🧪 Testing

```bash
# Run unit tests
mvn test

# Run integration tests
mvn verify

# Generate coverage report
mvn jacoco:report
```

Target coverage: 80% line coverage, 70% branch coverage

## 🔧 Configuration

Key configuration properties:

```yaml
# Database
spring.datasource.url: jdbc:postgresql://localhost:5432/expense_db
spring.datasource.username: expense_user

# Kafka
spring.kafka.bootstrap-servers: localhost:9092
kafka.dlq.enabled: true
kafka.retry.max-attempts: 3

# Redis
spring.data.redis.host: localhost
spring.data.redis.port: 6379

# Resilience
resilience4j.circuitbreaker.enabled: true
resilience4j.retry.enabled: true

# Notifications
notification.enabled: true
notification.email.enabled: true
notification.sms.enabled: true
notification.push.enabled: true
```

## 📝 Database Migrations

Flyway migrations in `src/main/resources/db/migration/`:

- `V1__initial_schema.sql` - Initial schema with expense_item, expense_report tables
- `V2__add_expenses_budgets_tables.sql` - Add expenses and budgets tables matching entities

## 🐛 Troubleshooting

### Common Issues

1. **Port already in use**
   ```bash
   lsof -ti:8055 | xargs kill -9
   ```

2. **Database connection failed**
   - Check PostgreSQL is running
   - Verify credentials in application.yml

3. **Kafka connection timeout**
   - Ensure Kafka broker is accessible
   - Check bootstrap server configuration

## 📚 Documentation

- **API Documentation**: http://localhost:8055/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8055/v3/api-docs

## 🤝 Contributing

1. Create feature branch
2. Implement changes with tests
3. Ensure 80%+ code coverage
4. Submit pull request

## 📄 License

Proprietary - Waqiti Platform

## 📞 Support

- Email: support@example.com
- Slack: #expense-service
- Documentation: https://docs.example.com/expense-service

## 🎯 Production Readiness Checklist

- [x] Comprehensive test coverage (80%+)
- [x] Database migrations
- [x] Circuit breakers implemented
- [x] Rate limiting configured
- [x] Health checks working
- [x] Metrics and monitoring
- [x] Distributed tracing
- [x] GDPR compliance endpoints
- [x] API documentation
- [x] Kubernetes manifests
- [x] Security scanning passed
- [x] Load testing completed

**Status**: ✅ PRODUCTION READY
