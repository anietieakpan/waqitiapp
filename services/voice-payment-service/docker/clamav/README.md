# ClamAV Deployment Guide

## Overview

ClamAV is an open-source antivirus engine for detecting trojans, viruses, malware, and other malicious threats. This deployment is used for scanning audio file uploads in the voice-payment-service.

## Quick Start

```bash
# Start ClamAV
cd /Users/anietieakpan/git/waqiti-app/services/voice-payment-service
docker-compose -f docker/clamav/docker-compose.yml up -d

# Check status
docker-compose -f docker/clamav/docker-compose.yml ps

# View logs
docker-compose -f docker/clamav/docker-compose.yml logs -f clamav

# Stop ClamAV
docker-compose -f docker/clamav/docker-compose.yml down
```

## Initial Startup

**IMPORTANT:** ClamAV takes 2-5 minutes to start on first run while it downloads virus signatures (~200MB).

Monitor startup progress:
```bash
docker logs -f waqiti-clamav
```

Wait for this message:
```
clamd[1]: Listening daemon: PID: XXX
clamd[1]: Reading databases from /var/lib/clamav
clamd[1]: Database correctly loaded (signatures: XXXXXX)
```

## Health Check

```bash
# Test ClamAV connectivity
echo "PING" | nc localhost 3310
# Expected response: PONG

# Test virus detection with EICAR test file
curl https://secure.eicar.org/eicar.com -o /tmp/eicar.com
echo "zINSTREAM" | nc localhost 3310
# Should detect: Eicar-Test-Signature FOUND
```

## Configuration

### Connection Settings

Update `application-security.yml`:
```yaml
voice-payment:
  security:
    clamav:
      enabled: true
      host: localhost  # Or Docker container name if in same network
      port: 3310
```

### Resource Limits

Current configuration:
- **CPU:** 1-2 cores
- **Memory:** 1-2 GB
- **Storage:** ~500MB for signatures

Adjust in `docker-compose.yml` if needed:
```yaml
deploy:
  resources:
    limits:
      cpus: '2.0'
      memory: 2G
```

## Signature Updates

ClamAV automatically updates virus signatures every 24 hours via `freshclam`.

Manual update:
```bash
docker exec waqiti-clamav freshclam
```

Check signature count:
```bash
docker exec waqiti-clamav clamdscan --version
```

## Production Deployment

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: clamav
  namespace: voice-payment
spec:
  replicas: 2  # High availability
  selector:
    matchLabels:
      app: clamav
  template:
    metadata:
      labels:
        app: clamav
    spec:
      containers:
      - name: clamav
        image: clamav/clamav:latest
        ports:
        - containerPort: 3310
          name: clamd
        resources:
          requests:
            memory: "1Gi"
            cpu: "1000m"
          limits:
            memory: "2Gi"
            cpu: "2000m"
        volumeMounts:
        - name: signatures
          mountPath: /var/lib/clamav
        livenessProbe:
          tcpSocket:
            port: 3310
          initialDelaySeconds: 120
          periodSeconds: 60
        readinessProbe:
          tcpSocket:
            port: 3310
          initialDelaySeconds: 120
          periodSeconds: 30
      volumes:
      - name: signatures
        persistentVolumeClaim:
          claimName: clamav-signatures
---
apiVersion: v1
kind: Service
metadata:
  name: clamav
  namespace: voice-payment
spec:
  selector:
    app: clamav
  ports:
  - port: 3310
    targetPort: 3310
    name: clamd
  type: ClusterIP
```

### Docker Swarm

```bash
docker service create \
  --name clamav \
  --replicas 2 \
  --publish 3310:3310 \
  --mount type=volume,source=clamav-signatures,target=/var/lib/clamav \
  --limit-cpu 2.0 \
  --limit-memory 2G \
  --reserve-cpu 1.0 \
  --reserve-memory 1G \
  --health-cmd "clamdcheck.sh" \
  --health-interval 60s \
  --health-timeout 10s \
  --health-retries 3 \
  --restart-condition on-failure \
  clamav/clamav:latest
```

## Monitoring

### Metrics

Monitor ClamAV performance:
```bash
# Check queue size
echo "STATS" | nc localhost 3310

# Check memory usage
docker stats waqiti-clamav

# Check signature count
docker exec waqiti-clamav sigtool --info /var/lib/clamav/main.cvd
```

### Logging

View logs:
```bash
# All logs
docker logs waqiti-clamav

# Real-time logs
docker logs -f waqiti-clamav

# Last 100 lines
docker logs --tail 100 waqiti-clamav
```

### Alerting

Set up alerts for:
- ✅ ClamAV container down
- ✅ Signature database outdated (>7 days)
- ✅ High memory usage (>1.5GB)
- ✅ High scan queue (>10 pending)
- ✅ Virus detections (any)

## Troubleshooting

### Container Won't Start

```bash
# Check logs
docker logs waqiti-clamav

# Common issues:
# 1. Port 3310 already in use
sudo lsof -i :3310

# 2. Insufficient memory
docker stats

# 3. Signature download failed
docker exec waqiti-clamav freshclam --verbose
```

### Slow Scans

```bash
# Increase threads
# Edit docker-compose.yml:
- CLAMD_CONF_MaxThreads=24

# Increase connection queue
- CLAMD_CONF_MaxConnectionQueueLength=30

# Restart
docker-compose -f docker/clamav/docker-compose.yml restart
```

### Outdated Signatures

```bash
# Force update
docker exec waqiti-clamav freshclam

# If update fails, check mirror
docker exec waqiti-clamav freshclam --list-mirrors
```

### High Memory Usage

```bash
# ClamAV typically uses 800MB-1.5GB

# If higher:
# 1. Check for memory leaks
docker stats waqiti-clamav

# 2. Restart container
docker restart waqiti-clamav

# 3. Reduce threads if needed
- CLAMD_CONF_MaxThreads=8
```

## Security Considerations

### Network Isolation

Production deployment should:
- ✅ Run in private network
- ✅ Only accessible from voice-payment-service
- ✅ Firewall rules restrict access
- ✅ No public exposure

### Updates

- ✅ Signature updates automatic (freshclam every 24h)
- ✅ Container image updates manual (test first)
- ✅ Monitor CVEs for ClamAV

### Backup

Signature database:
```bash
# Backup
docker run --rm -v clamav_signatures:/data -v $(pwd):/backup \
  busybox tar czf /backup/clamav-signatures-$(date +%Y%m%d).tar.gz /data

# Restore
docker run --rm -v clamav_signatures:/data -v $(pwd):/backup \
  busybox tar xzf /backup/clamav-signatures-YYYYMMDD.tar.gz -C /
```

## Performance Tuning

### For High Volume

```yaml
environment:
  - CLAMD_CONF_MaxThreads=24
  - CLAMD_CONF_MaxConnectionQueueLength=30
  - CLAMD_CONF_StreamMaxLength=50M

deploy:
  resources:
    limits:
      cpus: '4.0'
      memory: 4G
```

### For Low Resources

```yaml
environment:
  - CLAMD_CONF_MaxThreads=8
  - CLAMD_CONF_MaxConnectionQueueLength=10
  - CLAMD_CONF_StreamMaxLength=10M

deploy:
  resources:
    limits:
      cpus: '1.0'
      memory: 1G
```

## References

- [ClamAV Official Documentation](https://docs.clamav.net/)
- [ClamAV Docker Hub](https://hub.docker.com/r/clamav/clamav)
- [ClamAV GitHub](https://github.com/Cisco-Talos/clamav)

## Support

For issues:
1. Check logs: `docker logs waqiti-clamav`
2. Check ClamAV status: `echo "STATS" | nc localhost 3310`
3. Review [ClamAV FAQ](https://docs.clamav.net/faq/)
4. File issue with logs and configuration
