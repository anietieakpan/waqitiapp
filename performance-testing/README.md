# Performance Benchmarking - Waqiti Platform

## Overview

Comprehensive performance benchmarking suite validating production-readiness of the Waqiti fintech platform after Week 11-12 optimizations.

## Target Validation

### Database Query Optimization
- **Expected**: 80-95% query time reduction
- **243 indexes** across critical services
- Covering indexes for API endpoints
- Partial indexes on hot data (active/recent records)
- Composite indexes for complex queries

### Redis Caching Layer
- **Expected**: 95%+ cache hit rate
- Wallet balances: 5-minute TTL
- Fraud scores: 15-minute TTL
- Payment idempotency: 24-hour TTL
- Sub-5ms cache lookup latency

### Fraud Detection SLA
- **Critical SLA**: <100ms (p95)
- Sub-50ms database queries with optimized indexes
- Sub-5ms cache lookups for blocked transactions
- Real-time sanctions screening

### Wallet Balance Consistency
- **Critical SLA**: <0.1% error rate
- Zero overdrafts under concurrent operations
- Optimistic locking preventing race conditions
- ACID guarantees on all transfers

### Payment Processing Throughput
- **Expected**: 1000+ TPS
- p95 latency <500ms
- p99 latency <1000ms
- 99%+ success rate

## Benchmark Suite

### 1. Payment Service Benchmark
**File**: `jmeter/payment-service-benchmark.jmx`

**Test Scenarios**:
- Baseline payment creation (50 threads, 500 TPS target)
- Status query performance (100 threads, 1000 TPS) - validates covering indexes
- Idempotency duplicate detection (75 threads) - validates cache performance
- High-value transaction queries (30 threads) - validates partial indexes

**Key Assertions**:
- Status queries: <100ms with optimized indexes
- Cache lookups: <5ms
- Partial index queries: <50ms

**Duration**: 10 minutes per scenario

### 2. Fraud Detection Service Benchmark
**File**: `jmeter/fraud-detection-benchmark.jmx`

**Test Scenarios**:
- Real-time fraud checks (200 threads, 2000 TPS) - **CRITICAL SLA: <100ms**
- Blocked transaction cache lookup (150 threads) - validates <5ms cache
- Sanctions watchlist screening (100 threads) - validates index optimization
- Velocity rule evaluation (80 threads) - validates <75ms response

**Key Assertions**:
- Fraud check p95: <100ms (CRITICAL)
- Cache lookups: <5ms
- Sanctions screening: <50ms with optimized indexes

**Duration**: 10 minutes per scenario

### 3. Wallet Service Benchmark
**File**: `jmeter/wallet-service-benchmark.jmx`

**Test Scenarios**:
- Concurrent transfers (150 threads, 800 TPS) - validates optimistic locking
- Balance lookup cache (200 threads, 1500 TPS) - validates 95%+ hit rate
- Overdraft prevention (50 threads) - validates integrity checks
- Daily limit enforcement (30 threads) - validates Redis rate limiting
- Multi-currency exchange (60 threads) - validates cache + conversion performance

**Key Assertions**:
- Zero negative balances (CRITICAL)
- Cache hit rate: 95%+
- Cache lookups: <10ms
- No race conditions under concurrent load

**Duration**: 10 minutes per scenario

## Running Benchmarks

### Prerequisites

1. **JMeter Installation**:
```bash
brew install jmeter  # macOS
# or
wget https://dlcdn.apache.org//jmeter/binaries/apache-jmeter-5.6.2.tgz
tar -xzf apache-jmeter-5.6.2.tgz
export JMETER_HOME=/path/to/apache-jmeter-5.6.2
```

2. **Services Running**:
```bash
docker-compose up -d
```

3. **Monitoring Infrastructure**:
```bash
cd infrastructure
docker-compose -f docker-compose.monitoring.yml up -d
```

### Execute Full Benchmark Suite

```bash
cd performance-testing/scripts
./run-benchmarks.sh
```

**What it does**:
1. Checks prerequisites (JMeter, Docker, services health)
2. Starts Prometheus + Grafana for real-time monitoring
3. Runs all 3 benchmark suites sequentially with cooldown periods
4. Generates HTML reports for each service
5. Creates summary analysis
6. Compares results with baseline (if available)

### Run Individual Benchmarks

**Payment Service**:
```bash
$JMETER_HOME/bin/jmeter -n \
  -t jmeter/payment-service-benchmark.jmx \
  -l results/payment-service-results.jtl \
  -e -o reports/payment-service-report
```

**Fraud Detection Service**:
```bash
$JMETER_HOME/bin/jmeter -n \
  -t jmeter/fraud-detection-benchmark.jmx \
  -l results/fraud-detection-results.jtl \
  -e -o reports/fraud-detection-report
```

**Wallet Service**:
```bash
$JMETER_HOME/bin/jmeter -n \
  -t jmeter/wallet-service-benchmark.jmx \
  -l results/wallet-service-results.jtl \
  -e -o reports/wallet-service-report
```

## Analyzing Results

### 1. JMeter HTML Reports

Open `reports/{service}-report/index.html` in browser:
- **Statistics**: Min/Max/Avg/p90/p95/p99 latencies
- **Over Time**: Response times, throughput, active threads
- **Throughput**: Transactions per second
- **Response Times Percentiles**: Distribution analysis

### 2. Grafana Dashboards

**Real-time monitoring during benchmark**:
- Payment Service: http://localhost:3000/d/payment-service
- Fraud Detection: http://localhost:3000/d/fraud-detection  
- Wallet Service: http://localhost:3000/d/wallet-service
- Reconciliation: http://localhost:3000/d/reconciliation-service

**Key metrics to watch**:
- Cache hit rates (target: 95%+)
- Query execution times (target: 80-95% reduction)
- Fraud check latency (target: <100ms p95)
- Balance discrepancies (target: 0)
- Concurrent operation handling

### 3. Raw Results Files

**Location**: `results/{timestamp}/`
- `*-results.jtl`: Raw JMeter result data (CSV format)
- `BENCHMARK_SUMMARY.txt`: Executive summary

**Parse JTL files**:
```bash
awk -F',' '{sum+=$2; count++} END {print "Average:", sum/count "ms"}' results.jtl
```

## Success Criteria

### Database Query Optimization
- ✅ Status queries: 80-95% faster than pre-optimization
- ✅ Covering indexes eliminate table scans
- ✅ Partial indexes reduce index size by 60%+

### Redis Caching
- ✅ Cache hit rate: 95%+ for wallet balances
- ✅ Cache hit rate: 95%+ for payment idempotency
- ✅ Cache lookups: <5ms consistently

### Fraud Detection SLA
- ✅ p95 latency: <100ms (CRITICAL)
- ✅ p99 latency: <150ms
- ✅ Cache-assisted lookups: <5ms
- ✅ Sanctions screening: <50ms with optimized index

### Wallet Balance Integrity
- ✅ Zero balance discrepancies (<0.1%)
- ✅ Zero overdrafts under concurrent load
- ✅ Optimistic lock failures: <5% of attempts
- ✅ Cache consistency maintained

### Payment Processing
- ✅ Throughput: 1000+ TPS sustained
- ✅ Success rate: 99%+
- ✅ p95 latency: <500ms
- ✅ p99 latency: <1000ms

## Troubleshooting

### JMeter Connection Refused
```bash
Check services are running:
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

### Out of Memory Errors
```bash
Increase JMeter heap:
export HEAP="-Xms4g -Xmx8g"
$JMETER_HOME/bin/jmeter -n ...
```

### High Error Rate
```bash
Check service logs:
docker logs waqiti-payment-service
docker logs waqiti-fraud-detection-service
docker logs waqiti-wallet-service
```

### Cache Miss Rate High
```bash
Verify Redis is running:
docker exec -it waqiti-redis redis-cli PING

Check cache configuration:
curl http://localhost:8082/actuator/metrics/cache.gets
```

## Performance Baselines

### Pre-Optimization (Week 10)
- Payment status queries: ~800ms avg
- Fraud checks: ~250ms p95
- Cache hit rate: N/A (no caching)
- Wallet operations: 200 TPS max

### Post-Optimization Target (Week 13)
- Payment status queries: <100ms avg (87% improvement)
- Fraud checks: <100ms p95 (60% improvement)
- Cache hit rate: 95%+
- Wallet operations: 800 TPS (4x improvement)

## Next Steps After Benchmarking

1. **Week 15-16**: Security penetration testing
2. **Week 17-18**: Staging environment deployment
3. **Week 19-22**: Production readiness final review

## References

- Database optimizations: `services/*/src/main/resources/db/migration/V*__*_Query_Optimization.sql`
- Cache configuration: `services/common/src/main/java/com/waqiti/common/cache/`
- Integration tests: `services/*/src/test/java/*/integration/`
- Monitoring dashboards: `infrastructure/grafana/dashboards/`