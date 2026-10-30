# PROJECT SYNOPSIS

## 1. PROJECT IDENTIFICATION
- **Project Title:** CertChain — Blockchain-Based Academic Certificate Issuance and Verification System with Heuristic AI Tamper Detection
- **Domain:** Distributed Ledger Technology (DLT), Application Security, Full-Stack Software Engineering, Artificial Intelligence
- **Degree:** Bachelor of Technology (B.Tech) in Computer Science & Engineering
- **Student Candidates:** `[STUDENT NAME 1]`, `[STUDENT NAME 2]`, `[STUDENT NAME 3]`
- **Project Supervisor:** `[SUPERVISOR NAME]`, `[DESIGNATION]`, `[DEPARTMENT]`
- **Institution:** `[INSTITUTION / UNIVERSITY NAME]`, `[ACADEMIC YEAR]`

---

## 2. EXECUTIVE SUMMARY
Academic credential fraud and diploma mills represent a critical vulnerability for institutions and hiring organizations worldwide. Traditional credential verification relies on manual registrar inquiries, physical mail, or centralized relational databases prone to data manipulation, downtime, and SQL tampering. Furthermore, modern digital editing software allows bad actors to alter names, grades, or degree classifications in PDF certificates without leaving obvious visual traces.

**CertChain** addresses this challenge through a hybrid, decentralized credential management ecosystem. By combining an immutable Ethereum smart contract registry (`CertificateRegistry.sol`) with a high-performance Java 21 / Spring Boot 3 enterprise backend and a reactive React/Vite interface, CertChain guarantees tamper-proof provenance and sub-second public verification.

The platform introduces a dual-layer verification protocol:
1. **Deterministic Cryptographic Verification:** Computes canonical SHA-256 digests over certificate attributes and verifies them against the blockchain ledger.
2. **In-Process PDF Parsing & Heuristic AI Anomaly Detection:** Uses Apache PDFBox to parse uploaded credentials in-memory and an advisory rule-based AI engine to evaluate document structure, typography consistency, and semantic cohesion, reporting field-level diffs and explainable risk scores (`LOW`, `MEDIUM`, `HIGH`).

---

## 3. PROBLEM DEFINITION & OBJECTIVES
### Problem:
- Unchecked proliferation of fraudulent academic certificates and manipulated transcripts.
- Lack of trustless, automated verification mechanisms for employers and background check agencies.
- Vulnerability of centralized institutional repositories to single-point failure and insider tampering.
- Inability of traditional static QR codes to prevent malicious clone websites.

### Key Objectives:
1. Design and deploy a gas-optimized Ethereum smart contract (`CertificateRegistry.sol`) supporting immutable registration, verification, on-chain revocation, and institutional issuer whitelisting.
2. Implement a canonical SHA-256 serialization algorithm to eliminate false-positive tampering alarms.
3. Build client-side vector PDF generation with embedded high-density dynamic QR codes.
4. Develop in-process PDF text extraction (Apache PDFBox) capable of identifying field-level tampering (e.g. altered student name or grade).
5. Integrate an advisory heuristic AI anomaly engine that supplements cryptographic proof with explainable structural risk analysis.
6. Enforce enterprise Role-Based Access Control (`ADMIN`, `ISSUER`, `PUBLIC USER`) using stateless JWT tokens, BCrypt hashing, and persistent MongoDB audit logging.
7. Deliver a real-time administrative governance dashboard with search, filtering, metrics, and revocation safeguards.
8. Prepare containerized production deployment artifacts (Docker, Nginx, testnet scripts).

---

## 4. ARCHITECTURE & WORKFLOW
```
+-------------------------------------------------------------------------+
|                              PRESENTATION TIER                          |
|             React 18 + Vite SPA | jsPDF + qrcode.js Engine              |
+-------------------------------------------------------------------------+
                                    | (HTTPS REST / JSON)
                                    v
+-------------------------------------------------------------------------+
|                              APPLICATION TIER                           |
|       Java 21 / Spring Boot 3 | Spring Security 6 (JWT / BCrypt)        |
|  - CertificateService: Canonical SHA-256 Hashing & Verification Logic   |
|  - Apache PDFBox: In-Process PDF Text Parsing & Mismatch Extractor      |
|  - AiAnalysisService: Advisory Heuristic Structural Anomaly Detector    |
|  - AuditService: Comprehensive MongoDB Security Audit Event Logger      |
|  - BlockchainService: Web3j JSON-RPC Connector with Raw TX Signing      |
+-------------------------------------------------------------------------+
                    |                                  |
                    v (BSON / TCP)                     v (JSON-RPC 2.0)
+-------------------------------+      +----------------------------------+
|          DATA TIER            |      |          LEDGER TIER             |
|   MongoDB 7.0 Document DB     |      |  Ethereum Virtual Machine (EVM)  |
| - Users (Admin / Issuers)     |      | - CertificateRegistry.sol        |
| - Certificates Metadata       |      | - Canonical Hash Mapping         |
| - Audit Events Log            |      | - Authorized Issuer Whitelist    |
+-------------------------------+      +----------------------------------+
```

---

## 5. HARDWARE & SOFTWARE SPECIFICATIONS
- **Development Workstation:** Apple Silicon / x86_64, 16GB RAM, macOS / Linux / Windows
- **Backend Framework:** Spring Boot 3.2.0, Java 21 LTS (Temurin)
- **Frontend Framework:** React 18.3.1, Vite 5.4.2, Vanilla CSS3
- **Database:** MongoDB Community Server 7.0
- **Blockchain Environment:** Solidity 0.8.28, Hardhat 2.22.18, Web3j 4.10.3
- **Libraries:** Apache PDFBox 3.0.1, JJWT 0.12.6, jsPDF 4.2.1, qrcode 1.5.4

---

## 6. VERIFIED TESTING OUTCOMES
- **Smart Contract Test Suite:** 10 / 10 Passing (`pass 10, fail 0`)
- **Backend Unit & Integration Tests:** 71 / 71 Passing (`mvn test`)
- **Frontend Build & Linter:** Clean production build (`npm run build`), 0 linter issues (`oxlint`)
- **Live HTTP E2E Scenarios:** 11 / 11 Passing against running Spring Boot daemon and Hardhat node

---

## 7. CONCLUSION
CertChain successfully bridges cryptographic blockchain immutability with practical enterprise usability and intelligent document analysis. The prototype demonstrates a verifiable, cost-effective, and scalable framework for modern digital academic credentialing.
