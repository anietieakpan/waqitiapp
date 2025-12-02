# ⚠️ SHARED GDPR MODULE - DEPRECATED

## **Status: DEPRECATED - DO NOT USE**

**Deprecation Date:** October 2025
**Removal Target:** Version 2.0
**Replacement:** GDPR Service (`/services/gdpr-service`)

---

## 🚨 Important Notice

**This shared GDPR module is deprecated and will be removed in a future release.**

### Why Deprecated?

1. **Zero Adoption** - No services currently use this module
2. **Duplicate Functionality** - gdpr-service provides all the same features
3. **Incomplete Implementation** - 24 critical TODOs remain unfixed
4. **Better Alternative Exists** - gdpr-service is production-ready and comprehensive
5. **Architectural Clarity** - Centralized GDPR service is the recommended pattern

---

## 🔄 Migration Guide

### For New Development

**❌ DON'T DO THIS:**
```java
@Autowired
private GDPRDataPrivacyService gdprService; // DEPRECATED!

gdprService.handleDataExport(...); // Don't use this
```

**✅ DO THIS INSTEAD:**

#### Option 1: REST API (Recommended for external services)
```java
// Call GDPR Service REST API
POST https://api.example.com/gdpr-service/api/v1/gdpr/export
Authorization: Bearer {token}

{
  "userId": "user123",
  "format": "JSON",
  "categories": ["personal", "transactions"]
}
```

#### Option 2: Feign Client (Recommended for internal microservices)
```java
@FeignClient(name = "gdpr-service", path = "/api/v1/gdpr")
public interface GDPRServiceClient {

    @PostMapping("/export")
    ResponseEntity<DataExportDTO> exportUserData(
        @RequestParam String userId,
        @RequestParam ExportFormat format
    );

    @PostMapping("/consent")
    ResponseEntity<ConsentRecordDTO> grantConsent(
        @RequestBody GrantConsentDTO consent
    );

    @PostMapping("/requests")
    ResponseEntity<DataSubjectRequestDTO> createRequest(
        @RequestBody CreateRequestDTO request
    );
}
```

---

## 📊 Feature Comparison

| Feature | Shared GDPR Module (DEPRECATED) | GDPR Service (USE THIS) |
|---------|--------------------------------|------------------------|
| **Status** | ❌ Deprecated | ✅ Production-Ready |
| **REST API** | ❌ None | ✅ Complete (`/api/v1/gdpr/*`) |
| **Article 15 (Access)** | ⚠️ Incomplete | ✅ Full Implementation |
| **Article 17 (Erasure)** | ⚠️ Incomplete | ✅ Full Implementation |
| **Article 20 (Portability)** | ⚠️ Incomplete | ✅ Full Implementation |
| **Data Export (JSON/XML/CSV)** | ❌ Stub Only | ✅ Fully Implemented |
| **Notification Integration** | ❌ Stub Only | ✅ Email, SMS, Push |
| **Kafka Events** | ❌ None | ✅ Full Integration |
| **Security (OAuth2)** | ❌ None | ✅ Keycloak Integration |
| **Metrics/Monitoring** | ❌ None | ✅ Micrometer/Prometheus |
| **Redis Export Storage** | ❌ None | ✅ Implemented |
| **Deadline Tracking** | ⚠️ Basic | ✅ Advanced (30-day GDPR) |
| **Feign Clients** | ❌ None | ✅ 6 Clients (User, Transaction, etc.) |
| **Test Coverage** | ❌ None | ✅ Comprehensive |
| **Used By** | ❌ Zero Services | ✅ Platform-wide |

---

## 🏗️ Recommended Architecture

### **Centralized GDPR Service Pattern**

```
┌─────────────────────────────────────────────────────────────┐
│                     All Microservices                       │
│  (user-service, payment-service, wallet-service, etc.)     │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  │ Feign Clients / REST API
                  │
                  ↓
┌─────────────────────────────────────────────────────────────┐
│                      GDPR Service                           │
│                  (services/gdpr-service)                    │
│                                                             │
│  ✅ Single Source of Truth for GDPR Operations             │
│  ✅ Centralized Compliance                                  │
│  ✅ Unified Audit Trail                                     │
│  ✅ Easier Regulatory Reporting                             │
│                                                             │
│  Components:                                                │
│  - GDPRController (REST API)                                │
│  - GDPRComplianceService (Core Logic)                       │
│  - DataExportService (Data Collection)                      │
│  - ConsentManagementService                                 │
│  - DataAnonymizationService                                 │
│  - 6 Feign Clients (Data Collection Orchestration)         │
│  - Kafka Consumers (Event Processing)                       │
│  - Redis (Export Storage)                                   │
└─────────────────────────────────────────────────────────────┘
```

---

## 📖 GDPR Service Documentation

### Available Endpoints

| HTTP Method | Endpoint | Description |
|-------------|----------|-------------|
| `POST` | `/api/v1/gdpr/requests` | Create data subject request |
| `GET` | `/api/v1/gdpr/requests` | Get user's requests |
| `GET` | `/api/v1/gdpr/requests/{id}` | Get specific request |
| `GET` | `/api/v1/gdpr/requests/{id}/status` | Check request status |
| `DELETE` | `/api/v1/gdpr/requests/{id}` | Cancel request |
| `POST` | `/api/v1/gdpr/consent` | Grant consent |
| `DELETE` | `/api/v1/gdpr/consent/{purpose}` | Withdraw consent |
| `GET` | `/api/v1/gdpr/consent` | Get user consents |
| `GET` | `/api/v1/gdpr/consent/status` | Get consent status map |
| `GET` | `/api/v1/gdpr/export` | Export user data |
| `GET` | `/api/v1/gdpr/export/{id}/download` | Download export |
| `GET` | `/api/v1/gdpr/privacy/policy` | Get privacy policy |
| `GET` | `/api/v1/gdpr/privacy/rights` | Get data subject rights |

### Admin Endpoints (DPO/Admin Role)

| HTTP Method | Endpoint | Description |
|-------------|----------|-------------|
| `GET` | `/api/v1/gdpr/admin/requests/pending` | Get pending requests |
| `GET` | `/api/v1/gdpr/admin/requests/overdue` | Get overdue requests |
| `POST` | `/api/v1/gdpr/admin/consent/expired/process` | Process expired consents |

---

## 🔧 Integration Examples

### Example 1: Request Data Export

**User Service integrating with GDPR Service:**

```java
@Service
@RequiredArgsConstructor
public class UserDataExportService {

    private final GDPRServiceClient gdprClient;

    public void requestUserDataExport(String userId) {
        CreateRequestDTO request = CreateRequestDTO.builder()
            .requestType("DATA_ACCESS")
            .format("JSON")
            .userId(userId)
            .build();

        DataSubjectRequestDTO response = gdprClient.createRequest(request);

        log.info("Data export request created: {}", response.getRequestId());
    }
}
```

### Example 2: Check Consent

**Payment Service checking consent before processing:**

```java
@Service
@RequiredArgsConstructor
public class PaymentProcessingService {

    private final GDPRServiceClient gdprClient;

    public void processPayment(String userId, PaymentRequest payment) {
        // Check if user has valid consent for transaction processing
        Map<ConsentPurpose, Boolean> consents = gdprClient.getConsentStatus(userId);

        if (!consents.getOrDefault(ConsentPurpose.TRANSACTION_PROCESSING, false)) {
            throw new InsufficientConsentException(
                "User has not granted consent for transaction processing"
            );
        }

        // Proceed with payment...
    }
}
```

### Example 3: Kafka Event Integration

**Listen for GDPR events:**

```java
@Service
@RequiredArgsConstructor
public class GDPRErasureEventListener {

    @KafkaListener(topics = "gdpr.erasure.completed", groupId = "user-service")
    public void handleErasureCompleted(ErasureCompletedEvent event) {
        String userId = event.getUserId();

        // Clean up any remaining user-specific data in this service
        cleanupUserData(userId);

        log.info("Cleaned up data for erased user: {}", userId);
    }
}
```

---

## 🗑️ Removal Timeline

| Date | Action |
|------|--------|
| **Oct 2025** | Module marked as `@Deprecated` |
| **Nov 2025** | Deprecation warnings added to all classes |
| **Dec 2025** | Documentation updated with migration guide |
| **Jan 2026** | Final warning before removal |
| **Feb 2026** | **MODULE REMOVED** (v2.0 release) |

---

## 📞 Support & Questions

**For GDPR Service questions:**
- Documentation: `/services/gdpr-service/README.md`
- API Docs: https://api.example.com/gdpr-service/swagger-ui.html
- Slack: `#gdpr-compliance` channel
- Email: gdpr@example.com

**For Data Protection Officer (DPO):**
- Email: dpo@example.com
- Internal Extension: x5500

---

## ✅ Checklist for Migration

- [ ] Identify all code using shared GDPR module
- [ ] Replace with GDPR Service Feign client calls
- [ ] Update dependencies in `pom.xml`
- [ ] Test GDPR operations with new integration
- [ ] Remove shared GDPR module imports
- [ ] Update CI/CD pipelines if needed
- [ ] Document the migration in your service README

---

## 📚 Related Documentation

- [GDPR Service README](/services/gdpr-service/README.md)
- [GDPR Service API Documentation](/services/gdpr-service/docs/API.md)
- [Waqiti GDPR Compliance Guide](/compliance/gdpr/COMPLIANCE_GUIDE.md)
- [Architecture Decision Record (ADR): Centralized GDPR Service](/docs/adr/ADR-025-centralized-gdpr-service.md)

---

**Last Updated:** October 2025
**Maintained By:** Waqiti Platform Team
**Status:** DEPRECATED - Use GDPR Service instead
