# Feign Client Deprecation Guide - Payment Service

## Overview
This document tracks the deprecation of synchronous Feign clients in favor of event-driven architecture to break circular dependencies.

## Deprecated Clients

### 1. UserServiceClient
**Status**: DEPRECATED
**Replacement**: Kafka consumer for `user-created-events` topic
**Implementation**: `com.waqiti.payment.consumer.UserEventConsumer`

**Migration Steps**:
1. Replace all `userServiceClient.getUser()` calls with local cache lookups
2. Listen to user events via Kafka consumer
3. Maintain eventual consistency with local user cache
4. Remove `UserServiceClient` interface and fallback after migration complete

**Code Changes Required**:
```java
// OLD (Synchronous - Creates Circular Dependency)
UserResponse user = userServiceClient.getUser(userId);

// NEW (Event-Driven - Local Cache)
UserResponse user = userCacheService.getUserFromCache(userId);
// Cache is populated by UserEventConsumer listening to user-created-events
```

## Timeline
- Phase 1: Deploy Kafka consumers ✅
- Phase 2: Update all service calls to use cache (IN PROGRESS)
- Phase 3: Remove Feign client interfaces
- Phase 4: Verify no circular dependency in service mesh

## Testing Checklist
- [ ] Verify UserEventConsumer processes messages correctly
- [ ] Verify local cache is populated on user creation
- [ ] Verify payment flows work without synchronous user-service calls
- [ ] Load testing with eventual consistency model
