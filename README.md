# CertChain — Blockchain-Based Academic Certificate Verification System

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18.3.1-blue.svg)](https://react.dev/)
[![Solidity](https://img.shields.io/badge/Solidity-0.8.28-black.svg)](https://soliditylang.org/)
[![Ethereum](https://img.shields.io/badge/Blockchain-EVM%20Compatible-purple.svg)](https://ethereum.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Tests](https://img.shields.io/badge/Tests-75%20Passed%20(65%20BE%20%2B%2010%20SC)-success.svg)]()

> A complete, enterprise-grade, decentralized academic credential issuance, verification, and AI-assisted tamper-detection ecosystem engineered as a final-year B.Tech capstone project.

---

## 🚀 Key Highlights & Architecture
- **Immutable Smart Contract Registry:** Anchors canonical SHA-256 certificate hashes on Ethereum (`CertificateRegistry.sol`), establishing decentralized, tamper-proof academic provenance.
- **Infosys Springboard Verification & Tamper Detection:** Decodes Sunbird RC / W3C Verifiable Credentials (`Ed25519Signature2018`, `did:india`) from QR codes and verifies field consistency against visible PDF text to expose altered certificates.
- **In-Process PDFBox Tamper Detection:** Uploaded credentials are parsed natively in-memory to detect localized modifications (e.g. altered student name or grade) against authoritative records.
- **Advisory Heuristic AI Analysis:** Structural and lexical anomaly scoring (`LOW`, `MEDIUM`, `HIGH`) provides human-readable explanations without overriding cryptographic truth.
- **Role-Based Access Control (RBAC):** Strict separation between `ADMIN`, `ISSUER`, and unauthenticated `PUBLIC USER` using stateless HMAC-SHA256 JWT tokens.
- **Admin Governance Dashboard:** Real-time metrics, searchable and paginated certificate registries, on-chain revocation controls, issuer management, and persistent MongoDB audit logging.
- **Vector PDF & Dynamic QR Codes:** High-resolution landscape PDF certificate generation with embedded 2D QR codes linking directly to verification URLs.

---

## 📁 Repository Structure
```
blockchain-certificate-system/
├── backend/                   # Java 21 / Spring Boot 3 Enterprise REST API
│   ├── src/main/java/         # Controllers, Services, Security Filters, Models
│   ├── src/test/java/         # 65 Automated Unit and Integration Tests
│   ├── Dockerfile             # Multi-stage Maven build with JRE 21 runtime
│   └── pom.xml                # Dependencies: Web3j, PDFBox, JJWT, Spring Security
├── frontend/                  # React 18 + Vite 5 Single Page Application
│   ├── src/pages/             # Issue, Verify, Dashboard, Login, Home
│   ├── src/components/        # Navbar, Modals, Verification Badges
│   ├── src/utils/             # pdfGenerator.js, api.js
│   ├── Dockerfile             # Multi-stage Vite build with Nginx SPA routing
│   └── nginx.conf             # Production Nginx SPA fallback configuration
├── blockchain/                # Ethereum Smart Contracts & Hardhat Environment
│   ├── contracts/             # CertificateRegistry.sol (Solidity 0.8.28)
│   ├── scripts/               # deploy.ts, deploy-testnet.ts
│   └── test/                  # 10 Hardhat TypeScript Unit Tests
├── docs/                      # Academic Final-Year Project Documentation
│   ├── final-year-project-report.md  # Complete 46-Section Academic Report
│   ├── PROJECT_SYNOPSIS.md           # Executive Synopsis for Evaluation Panel
│   ├── PRESENTATION_OUTLINE.md       # Slide-by-Slide Viva Defense Guide
│   ├── API_REFERENCE.md              # REST API OpenAPI Specification
│   └── DEPLOYMENT_GUIDE.md           # Production Deployment & Docker Manual
├── docker-compose.yml         # Container Orchestration (Frontend, Backend, MongoDB)
├── .env.example               # Template for environment variables and secrets
└── README.md                  # Project Overview and Quickstart Guide
```

---

## ⚡ Quickstart Guide (Local Development)

### Prerequisites:
- Java 21 LTS
- Node.js >= 18.x
- Maven >= 3.9
- MongoDB running locally on `localhost:27017`

### Step 1: Start Blockchain Node & Deploy Contract
```bash
cd blockchain
npm install
npx hardhat node
# In a separate terminal:
npx hardhat run scripts/deploy.ts --network localhost
```

### Step 2: Start Spring Boot Backend
```bash
cd ../backend
ADMIN_PASSWORD=PranavAdmin@2024 mvn spring-boot:run
# Server boots on http://localhost:8080
```

### Step 3: Start React Frontend
```bash
cd ../frontend
npm install
npm run dev
# Interface opens on http://localhost:5173
```

---

## 🧪 Verified Automated Test Results

| Test Suite | Framework / Tool | Tests Run | Passed | Failures | Status |
| :--- | :--- | :---: | :---: | :---: | :---: |
| **Smart Contract** | Hardhat / Chai | 10 | 10 | 0 | **PASS** |
| **Backend Unit & Slice** | JUnit 5 / Mockito | 71 | 71 | 0 | **PASS** |
| **Frontend Static** | Oxlint / Vite Build | - | Clean | 0 | **PASS** |
| **Live E2E Scenarios** | Automated HTTP Suite | 11 | 11 | 0 | **PASS** |

---

## 📚 Complete Project Documentation
- [Comprehensive Final-Year Project Report (46 Sections)](docs/final-year-project-report.md)
- [Project Synopsis for Review Committee](docs/PROJECT_SYNOPSIS.md)
- [Viva Voce Presentation Outline](docs/PRESENTATION_OUTLINE.md)
- [REST API Reference Manual](docs/API_REFERENCE.md)
- [Production Deployment Guide](docs/DEPLOYMENT_GUIDE.md)

---

## 📄 License
This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
