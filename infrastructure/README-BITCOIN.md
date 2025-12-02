# Bitcoin & Lightning Network Infrastructure for Waqiti

This document describes the complete Bitcoin and Lightning Network infrastructure required for Waqiti's P2P payment platform.

## 🚀 Quick Start

### 1. Setup Bitcoin Infrastructure
```bash
cd infrastructure/scripts
chmod +x setup-bitcoin-infrastructure.sh
./setup-bitcoin-infrastructure.sh
```

### 2. Start Waqiti with Bitcoin Support
```bash
cd infrastructure/docker
docker-compose --profile bitcoin up -d
```

### 3. Verify Setup
```bash
# Check Bitcoin node
curl -u waqiti:$BITCOIN_RPC_PASSWORD http://localhost:8332 \
  -d '{"jsonrpc":"1.0","method":"getblockchaininfo","params":[]}'

# Check Lightning node  
curl http://localhost:8080/v1/getinfo
```

## 📋 Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Waqiti Application                        │
├─────────────────────────────────────────────────────────────┤
│              Crypto Service (Lightning API)                  │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────┐ │
│  │   Invoice   │ │   Payment   │ │       Channel           │ │
│  │   Service   │ │   Service   │ │       Service           │ │
│  └─────────────┘ └─────────────┘ └─────────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│                Lightning Network Daemon (LND)                │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  • Payment Channels    • LNURL Support                │ │
│  │  • Routing             • Keysend Payments             │ │
│  │  • Invoice Generation  • Submarine Swaps              │ │
│  └─────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│                    Bitcoin Core Node                         │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  • Blockchain Sync     • Transaction Validation       │ │
│  │  • Mempool Management  • P2P Network Communication    │ │
│  │  • RPC Interface       • ZMQ Notifications            │ │
│  └─────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│                     Bitcoin Network                          │
└─────────────────────────────────────────────────────────────┘
```

## 🛠 Components

### Core Infrastructure

#### Bitcoin Core Node
- **Purpose**: Blockchain synchronization and validation
- **Port**: 8332 (RPC), 8333 (P2P)
- **Storage**: ~350GB (mainnet), ~25GB (testnet)
- **Configuration**: Optimized for Lightning Network

#### Lightning Network Daemon (LND)
- **Purpose**: Lightning payment processing
- **Port**: 10009 (gRPC), 8080 (REST), 9735 (P2P)
- **Features**: Multi-path payments, keysend, LNURL, channels
- **Storage**: Channel state and routing data

### Management Tools

#### RTL (Ride The Lightning)
- **Purpose**: Web UI for Lightning node management  
- **Port**: 3000
- **Features**: Channel management, payment history, routing

#### Lightning Terminal
- **Purpose**: Advanced Lightning operations
- **Port**: 8443
- **Features**: Loop, Pool, Faraday integration

#### BTCPay Server (Optional)
- **Purpose**: Merchant payment processing
- **Port**: 3003
- **Features**: Invoice generation, payment tracking

## ⚙️ Configuration

### Environment Variables

```bash
# Network Selection
BITCOIN_NETWORK=testnet          # mainnet, testnet, regtest

# Security
BITCOIN_RPC_PASSWORD=<secure-password>
LND_PASSWORD=<lnd-wallet-password>
RTL_PASSWORD=<rtl-ui-password>

# External Access
LND_EXTERNAL_IP=<your-external-ip>

# Backup Encryption
BITCOIN_BACKUP_ENCRYPTION_KEY=<32-byte-key>
LIGHTNING_CHANNEL_BACKUP_KEY=<32-byte-key>
```

### Bitcoin Configuration (`bitcoin.conf`)
```ini
# Network
testnet=1                        # Use testnet for development
server=1
daemon=1

# RPC Settings  
rpcuser=waqiti
rpcpassword=<secure-password>
rpcallowip=0.0.0.0/0
rpcbind=0.0.0.0:8332

# Lightning Requirements
txindex=1                        # REQUIRED for Lightning
zmqpubrawblock=tcp://0.0.0.0:28332
zmqpubrawtx=tcp://0.0.0.0:28333
prune=0                          # No pruning for Lightning

# Performance
dbcache=1000
maxconnections=50
```

### Lightning Configuration (`lnd.conf`)
```ini
[Application Options]
alias=Waqiti-Lightning-Node
restlisten=0.0.0.0:8080
rpclisten=0.0.0.0:10009

[Bitcoin]  
bitcoin.active=1
bitcoin.testnet=1
bitcoin.node=bitcoind

[Bitcoind]
bitcoind.rpchost=bitcoin-core:8332
bitcoind.rpcuser=waqiti
bitcoind.rpcpass=<secure-password>
bitcoind.zmqpubrawblock=tcp://bitcoin-core:28332
bitcoind.zmqpubrawtx=tcp://bitcoin-core:28333

# Features
accept-keysend=true
accept-amp=true
protocol.wumbo-channels=true
```

## 💾 Data Storage

### Persistent Volumes
```yaml
volumes:
  bitcoin-data:     # Bitcoin blockchain (~350GB mainnet)
  lnd-data:         # Lightning channels and routing data  
  electrs-data:     # Electrum server index
  rtl-data:         # RTL configuration
  shared-data:      # TLS certs and macaroons
```

### Database Schema
Lightning operations are stored in PostgreSQL:
- `lightning_invoices` - Invoice tracking and status
- `lightning_payments` - Payment history and routing
- `lightning_channels` - Channel state and balances
- `lightning_streams` - Recurring payment streams
- `lightning_swaps` - Submarine swap operations

## 🔒 Security

### Network Security
- TLS encryption for all Lightning communications
- Macaroon-based authentication for LND access
- IP whitelisting for Bitcoin RPC
- Firewall rules for external access

### Backup Strategy
- Automated channel backups to secure storage
- Encrypted wallet seed phrase storage
- Database backups with point-in-time recovery
- Infrastructure as code for disaster recovery

### Access Control
```yaml
# Role-based permissions in Waqiti
USER:     # Basic Lightning operations
  - Create invoices
  - Make payments
  - View payment history

MERCHANT: # Advanced Lightning features  
  - Manage channels
  - Configure fees
  - Access analytics

ADMIN:    # Full Lightning control
  - Node management
  - Peer connections
  - Channel backup/restore
```

## 📊 Monitoring

### Health Checks
The crypto-service includes comprehensive health indicators:
- Bitcoin node connectivity and sync status
- Lightning node operational status
- Channel health and balance monitoring
- Certificate and authentication validation

### Metrics Collection
```yaml
# Prometheus metrics
bitcoin_blocks_total          # Blockchain height
bitcoin_connections_total     # P2P connections
lightning_channels_active     # Active channels
lightning_payments_total      # Payment counters
lightning_balance_local       # Channel balances
lightning_fees_earned         # Fee earnings
```

### Alerting Rules
Critical alerts for:
- Bitcoin node out of sync
- Lightning node offline
- Channel force closures
- Payment failures > threshold
- Low channel liquidity

## 🚀 Production Deployment

### Hardware Requirements

#### Minimum (Testnet Development)
- **CPU**: 2 cores
- **RAM**: 4GB
- **Storage**: 50GB SSD
- **Network**: 100 Mbps

#### Recommended (Mainnet Production)
- **CPU**: 8 cores
- **RAM**: 16GB  
- **Storage**: 500GB NVMe SSD
- **Network**: 1 Gbps
- **Backup**: 1TB external storage

### Performance Tuning

#### Bitcoin Core Optimizations
```ini
# Database cache (use 50% of RAM)
dbcache=8000

# Connection limits
maxconnections=100

# Block processing  
par=8
```

#### Lightning Network Optimizations
```ini
# Channel settings
autopilot.active=true
autopilot.maxchannels=20
autopilot.allocation=0.6

# Payment optimization
routing.strictgraph=true
routing.assumechanvalid=true
```

## 🔧 Maintenance

### Regular Tasks
```bash
# Check sync status
bitcoin-cli getblockchaininfo
lncli getinfo

# Monitor channels
lncli listchannels
lncli channelbalance

# Backup channels  
lncli exportchanbackup --all backup.dat

# Update fee policies
lncli updatechanpolicy --base_fee_msat 1000 --fee_rate 0.000001
```

### Troubleshooting

#### Bitcoin Node Issues
```bash
# Check logs
docker logs waqiti-bitcoin-core

# Restart sync
docker restart waqiti-bitcoin-core

# Verify connectivity
curl -u waqiti:$PASSWORD http://localhost:8332 \
  -d '{"method":"getconnectioncount"}'
```

#### Lightning Issues
```bash
# Check LND status
docker logs waqiti-lnd
lncli getinfo

# Restart LND
docker restart waqiti-lnd

# Unlock wallet
echo $LND_PASSWORD | lncli unlock
```

## 📚 API Integration

### Lightning Service Endpoints
```http
POST /api/v1/lightning/invoices      # Create invoice
GET  /api/v1/lightning/invoices      # List invoices
POST /api/v1/lightning/payments      # Send payment
GET  /api/v1/lightning/channels      # List channels
POST /api/v1/lightning/channels      # Open channel
GET  /api/v1/lightning/statistics    # Get statistics
```

### Example Usage
```javascript
// Create Lightning invoice
const invoice = await fetch('/api/v1/lightning/invoices', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    amountSat: 100000,
    description: 'Payment for services',
    expirySeconds: 3600
  })
});

// Pay Lightning invoice  
const payment = await fetch('/api/v1/lightning/payments', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    paymentRequest: 'lnbc1m1p...',
    maxFeeSat: 1000
  })
});
```

## ⚠️ Important Notes

### Testnet vs Mainnet
- **Development**: Always use testnet
- **Staging**: Use testnet with mainnet-like load
- **Production**: Only use mainnet with proper security

### Fee Management
- Monitor Bitcoin transaction fees
- Adjust Lightning routing fees based on demand
- Reserve funds for channel closures

### Backup Strategy
- Store channel backups off-site
- Test recovery procedures regularly
- Keep wallet seed phrases secure

### Legal Compliance
- Understand local Bitcoin regulations
- Implement AML/KYC as required
- Monitor transaction limits and reporting

## 📞 Support

For Bitcoin infrastructure issues:
1. Check the health indicators: `/actuator/health`
2. Review application logs: `docker logs waqiti-crypto-service`
3. Validate Bitcoin node: `bitcoin-cli getblockchaininfo` 
4. Check Lightning status: `lncli getinfo`
5. Monitor RTL dashboard: `http://localhost:3000`

For advanced troubleshooting, consult:
- [Bitcoin Core Documentation](https://bitcoin.org/en/bitcoin-core/)
- [LND Documentation](https://docs.lightning.engineering/)
- [Lightning Network Specifications](https://github.com/lightning/bolts)