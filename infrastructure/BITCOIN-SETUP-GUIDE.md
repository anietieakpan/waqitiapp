# Bitcoin Infrastructure Setup Guide - When and How to Run

This guide explains when and how to run the `setup-bitcoin-infrastructure.sh` script for the Waqiti platform.

## 🎯 Primary Use Cases

### 1. **Initial Development Setup** (Most Common)
```bash
# When a developer first clones the Waqiti repo and wants to work on Lightning features
git clone https://github.com/waqiti/waqiti-app
cd waqiti-app/infrastructure/scripts
./setup-bitcoin-infrastructure.sh
```
**Purpose**: Sets up the complete Bitcoin infrastructure needed for Lightning development

### 2. **New Environment Deployment**
```bash
# Setting up staging/production environments
# Run ONCE per environment
./setup-bitcoin-infrastructure.sh
```
**Purpose**: Bootstrap Bitcoin infrastructure in fresh environments

### 3. **Infrastructure Recovery**
```bash
# After major system failures or infrastructure rebuild
./setup-bitcoin-infrastructure.sh
```
**Purpose**: Restore Bitcoin services after disasters

## ⏰ When NOT to Run It

### ❌ **Don't run on every deployment**
```bash
# WRONG - Don't do this in CI/CD
deploy.sh:
  ./setup-bitcoin-infrastructure.sh  # ❌ Bad!
  docker-compose up -d waqiti-services
```

### ❌ **Don't run if Bitcoin is already running**
```bash
# Check first
docker ps | grep bitcoin-core
# If Bitcoin containers exist, DON'T run setup script
```

## 🔄 Proper Integration with Deployment

### Development Workflow
```bash
# 1. One-time setup (per developer/machine)
./setup-bitcoin-infrastructure.sh

# 2. Daily development (restart services only)
docker-compose --profile bitcoin up -d

# 3. Code changes (restart app services only)
docker-compose restart crypto-service
```

### Production Deployment
```bash
# 1. Initial production setup (ONCE)
BITCOIN_NETWORK=mainnet ./setup-bitcoin-infrastructure.sh

# 2. Application deployments (daily/weekly)
docker-compose up -d --no-deps crypto-service payment-service

# 3. Infrastructure maintenance (monthly)
# Don't re-run setup script - use targeted updates
```

## 📋 Prerequisites Check

**Before running the script**, ensure:

```bash
# 1. Docker is installed
docker --version
docker-compose --version

# 2. Sufficient disk space
df -h  # Need 50GB+ for testnet, 400GB+ for mainnet

# 3. No conflicting Bitcoin services
docker ps | grep -E "(bitcoin|lnd)"  # Should be empty

# 4. Proper permissions
ls -la /var/run/docker.sock  # Should be accessible
```

## 🎛 Configuration Before Running

### Environment Setup
```bash
# Set network (critical decision!)
export BITCOIN_NETWORK=testnet  # or mainnet

# Set external IP for Lightning
export LND_EXTERNAL_IP=your.external.ip

# Run setup
./setup-bitcoin-infrastructure.sh
```

## ⚠️ Important Warnings

### 🚨 **Mainnet Warning**
```bash
# This uses REAL BITCOIN - be very careful!
BITCOIN_NETWORK=mainnet ./setup-bitcoin-infrastructure.sh
```

### 💾 **Data Persistence Warning** 
The script creates persistent Docker volumes:
```bash
docker volume ls | grep bitcoin
# bitcoin-data    - Contains entire blockchain
# lnd-data        - Contains Lightning channels (CRITICAL!)
```
**These volumes survive container restarts but need proper backup!**

### ⏳ **Time Warning**
- **Testnet sync**: 2-6 hours
- **Mainnet sync**: 2-7 days (depending on hardware/network)

## 🔧 Script Behavior

The setup script is **idempotent** (safe to re-run):

```bash
# First run: Sets up everything
./setup-bitcoin-infrastructure.sh
# ✅ Creates containers, starts sync

# Second run: Checks existing state  
./setup-bitcoin-infrastructure.sh
# ✅ Skips setup, validates health

# After problems: Recovers gracefully
./setup-bitcoin-infrastructure.sh  
# ✅ Restarts failed services, preserves data
```

## 📚 Integration Example

Here's how it fits into a typical development workflow:

```bash
# 1. NEW DEVELOPER ONBOARDING (run once)
git clone waqiti-app
cd infrastructure/scripts
./setup-bitcoin-infrastructure.sh    # ← Run the script here
# Wait for Bitcoin to sync...

# 2. DAILY DEVELOPMENT (normal workflow)
cd infrastructure/docker
docker-compose up -d                  # ← Normal startup
# Make code changes...
docker-compose restart crypto-service # ← App updates only

# 3. TESTING LIGHTNING FEATURES
# Bitcoin infrastructure already running from step 1
# Just test your Lightning code against it
```

## 🎯 Summary

**Run the setup script:**
- ✅ Once per developer machine (initial setup)
- ✅ Once per environment (staging, production)  
- ✅ After infrastructure disasters
- ✅ When switching networks (testnet ↔ mainnet)

**Don't run the script:**
- ❌ On every code deployment
- ❌ In CI/CD pipelines (unless setting up fresh environments)
- ❌ When Bitcoin containers are already running
- ❌ For routine application restarts

## 🔍 Troubleshooting

### Script Fails to Start
```bash
# Check Docker
systemctl status docker
docker info

# Check disk space
df -h

# Check ports
netstat -tlnp | grep -E "(8332|8333|10009|8080)"
```

### Bitcoin Sync Issues
```bash
# Check Bitcoin logs
docker logs waqiti-bitcoin-core

# Check sync progress
docker exec waqiti-bitcoin-core bitcoin-cli -rpcuser=waqiti -rpcpassword=$BITCOIN_RPC_PASSWORD getblockchaininfo
```

### Lightning Connection Issues
```bash
# Check LND logs
docker logs waqiti-lnd

# Check LND status
docker exec waqiti-lnd lncli --network=testnet getinfo
```

## 🎯 Key Insight

**Bitcoin infrastructure setup is separate from application deployment** - you set up the Bitcoin foundation once, then deploy your Waqiti services on top of it many times.

This separation allows for:
- **Stable infrastructure** - Bitcoin keeps running through app deployments
- **Faster deployments** - No need to restart Bitcoin for app changes
- **Data preservation** - Blockchain and channel data persist across updates
- **Independent scaling** - Infrastructure and applications can scale differently