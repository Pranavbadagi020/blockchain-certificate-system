# CERTCHAIN PRODUCTION DEPLOYMENT GUIDE

This manual provides comprehensive, step-by-step instructions for deploying CertChain into production using Docker Compose, Nginx, MongoDB Atlas / local MongoDB, and an EVM-compatible public testnet (such as Sepolia).

---

## 1. SYSTEM REQUIREMENTS
- **Operating System:** Ubuntu 22.04 LTS / Debian 12 / macOS / RHEL 9
- **Hardware Minimums:** 2 vCPU, 4GB RAM, 20GB SSD Storage
- **Installed Software:**
  - Docker Engine >= 24.0 & Docker Compose >= 2.20
  - Node.js >= 18.x (for smart contract deployment)
  - OpenSSL (for generating secure production secrets)

---

## 2. PRODUCTION ENVIRONMENT CONFIGURATION
Create a `.env` file in the project root:

```bash
# ==========================================
# CERTCHAIN PRODUCTION CONFIGURATION (.env)
# ==========================================

# Active Profile
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080

# MongoDB Configuration
SPRING_DATA_MONGODB_URI=mongodb://certchain_db:27017/certchain
MONGO_INITDB_ROOT_USERNAME=admin
MONGO_INITDB_ROOT_PASSWORD=GenerateStrongDbPasswordHere

# Security & JWT Configuration (Generate via: openssl rand -hex 32)
JWT_SECRET=e4d9b6f8a1c3e5a7b9d2f4a6c8e0b2d4f6a8c0e2b4d6f8a0c2e4b6d8f0a2c4e6
JWT_EXPIRATION_MS=86400000

# Initial Admin Credentials (Minimum 12 characters)
ADMIN_USERNAME=admin
ADMIN_PASSWORD=ComplexAdminPassword2026!

# Ethereum Blockchain Configuration
# For Sepolia: https://sepolia.infura.io/v3/YOUR_INFURA_KEY
BLOCKCHAIN_RPC_URL=https://sepolia.infura.io/v3/YOUR_INFURA_KEY
BLOCKCHAIN_PRIVATE_KEY=YOUR_64_CHAR_HEX_PRIVATE_KEY
CONTRACT_ADDRESS=0xYourDeployedContractAddressOnSepolia

# Frontend Configuration
VITE_API_BASE_URL=/api
AI_PROVIDER=heuristic
```

> **SECURITY NOTE:** Never commit `.env` or production private keys into version control. Ensure `.env` is listed in `.gitignore`.

---

## 3. STEP-BY-STEP DEPLOYMENT WORKFLOW

### Step 3.1: Deploy Smart Contract to Sepolia Testnet
1. Navigate to the blockchain directory:
   ```bash
   cd blockchain
   npm install
   ```
2. Run the deployment script targeting the Sepolia network:
   ```bash
   SEPOLIA_RPC_URL="https://sepolia.infura.io/v3/YOUR_INFURA_KEY"    PRIVATE_KEY="YOUR_64_CHAR_HEX_KEY"    npx hardhat run scripts/deploy-testnet.ts --network sepolia
   ```
3. Record the returned `Contract Address` and update `CONTRACT_ADDRESS` in your `.env` file.

### Step 3.2: Build & Start Containerized Services
From the repository root:
```bash
# Build multi-stage images and start daemon
docker-compose up --build -d
```

### Step 3.3: Verify Deployment Health
Check running containers:
```bash
docker-compose ps
```
Expected output:
- `certchain-db` (MongoDB) -> `Up (healthy) 27017/tcp`
- `certchain-backend` (Spring Boot Java 21) -> `Up (healthy) 8080/tcp`
- `certchain-frontend` (Nginx + React SPA) -> `Up 80/tcp -> 0.0.0.0:80->80/tcp`

Inspect backend startup logs:
```bash
docker-compose logs -f backend
```
Look for:
- `Connected to MongoDB`
- `Admin account initialized / synchronized`
- `Started CertificateBackendApplication in X.XXX seconds`

---

## 4. CONFIGURE DOMAIN & SSL (HTTPS) WITH NGINX REVERSE PROXY
For public internet deployment, route traffic through a host-level Nginx reverse proxy with Certbot SSL certificates:

```nginx
server {
    listen 80;
    server_name certchain.institution.edu;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name certchain.institution.edu;

    ssl_certificate /etc/letsencrypt/live/certchain.institution.edu/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/certchain.institution.edu/privkey.pem;

    # Security Headers
    add_header X-Frame-Options "DENY" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Content-Security-Policy "default-src 'self' 'unsafe-inline' https:; img-src 'self' data: https:;" always;

    # Frontend Single-Page Application
    location / {
        proxy_pass http://127.0.0.1:80;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Backend REST API
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 10M;
    }
}
```

---

## 5. DATABASE BACKUP AND DISASTER RECOVERY
- **Daily Automated Backup Command:**
  ```bash
  docker exec -t certchain-db mongodump --out /data/backup/$(date +%Y-%m-%d)
  ```
- **Restore Command:**
  ```bash
  docker exec -t certchain-db mongorestore /data/backup/YYYY-MM-DD/certchain
  ```
- **Blockchain Ledger Immutability:** Because all canonical certificate hashes and revocation statuses reside on Ethereum, in the catastrophic event of a total database loss, all certificates remain verifiable directly from the blockchain smart contract events.
