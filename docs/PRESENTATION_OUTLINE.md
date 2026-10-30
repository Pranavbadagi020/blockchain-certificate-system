# CERTCHAIN — FINAL-YEAR PROJECT VIVA / DEFENSE OUTLINE

**Project Title:** CertChain: Blockchain-Based Certificate Verification System with AI-Assisted Tamper Detection  
**Target Duration:** 20 - 25 Minutes (Presentation + Live Demonstration + Q&A)

---

## SLIDE 1: TITLE SLIDE
- **Title:** CertChain
- **Subtitle:** Decentralized Academic Credential Verification with Heuristic AI Tamper Detection
- **Team Members:** `[STUDENT NAME 1]`, `[STUDENT NAME 2]`, `[STUDENT NAME 3]`
- **Guide / Supervisor:** `[SUPERVISOR NAME]`
- **Department & Institution:** `[DEPARTMENT]`, `[INSTITUTION NAME]`

## SLIDE 2: THE REAL-WORLD PROBLEM
- Proliferation of fake degrees, diploma mills, and edited PDF transcripts.
- Inefficiencies in traditional university verification: 3 to 21 business days delay.
- Centralized database risks: single point of failure, insider manipulation, SQL injection.
- The "Fake QR Code" fallacy: static QR codes pointing to attacker-controlled spoof websites.

## SLIDE 3: PROJECT MOTIVATION & OBJECTIVES
- Establish an immutable root of trust using Ethereum smart contracts.
- Implement deterministic canonical SHA-256 hashing across certificate attributes.
- Provide automated, field-level PDF tamper detection (detecting altered student names or grades).
- Introduce an advisory heuristic AI anomaly layer for explainable risk assessment.
- Build an enterprise-grade admin governance dashboard with RBAC and full audit trails.

## SLIDE 4: SYSTEM ARCHITECTURE
- Decoupled 3-tier hybrid architecture (React/Vite -> Spring Boot 3 / Java 21 -> MongoDB + Ethereum EVM).
- Web3j JSON-RPC connector for transaction signing and contract calls.
- Role-Based Access Control: `ADMIN`, `ISSUER`, `PUBLIC USER`.
- *Display Mermaid Architecture Diagram*.

## SLIDE 5: SMART CONTRACT DESIGN (`CertificateRegistry.sol`)
- Solidity 0.8.28 with custom error handling and gas optimization.
- Storage mapping: `bytes32` canonical hash -> `CertificateRecord` struct.
- Access Control: `onlyAuthorizedIssuer` modifier and dynamic issuer whitelisting.
- On-chain functions: `issueCertificate()`, `verifyCertificate()`, `revokeCertificate()`.
- Zero gas cost for public verifications (`view` function).

## SLIDE 6: CANONICAL HASHING & TAMPER DETECTION
- Normalization rules: lowercase conversion, whitespace collapsing, pipe (`|`) delimiter.
- The cryptographic avalanche effect: changing 1 character completely alters the SHA-256 digest.
- In-process PDF text extraction using Apache PDFBox.
- Automatic field diff table highlighting specific altered attributes.

## SLIDE 7: ADVISORY AI ANOMALY DETECTION
- Non-override governance rule: AI never overrides deterministic cryptographic verification.
- 7 Heuristic indicators: text density, future timestamps, lexical contradictions, structural keywords, editing tool metadata, placeholder collisions, font homogeneity.
- Risk classification: `LOW`, `MEDIUM`, `HIGH`.
- Actionable narrative and guidance for human verification officers.

## SLIDE 8: SECURITY, RBAC & AUDIT LOGGING
- Stateless HMAC-SHA256 JWT tokens with 24-hour expiration.
- Passwords protected via BCrypt with work factor 10.
- MongoDB `audit_events` collection tracking all logins, issuances, revocations, and tamper alerts.
- Secrets externalized via environment variables (never exposed in client code).

## SLIDE 9: ADMIN DASHBOARD & GOVERNANCE
- Live operational metrics (Total Issued, Valid, Revoked, Tamper Alerts).
- Searchable, paginated certificate records with multi-field filtering.
- Two-step confirmation modal for on-chain revocations with administrative reason logging.
- Institutional issuer onboarding and management.

## SLIDE 10: AUTOMATED TESTING & VERIFICATION RESULTS
- Smart Contract Unit Tests: 10 / 10 Passed (Hardhat).
- Backend Unit & Integration Tests: 71 / 71 Passed (Maven / JUnit 5).
- Frontend Static Analysis: Clean Vite build, 0 Oxlint errors.
- Live E2E Integration: 11 / 11 Scenarios Passed against running daemon and Hardhat node.

## SLIDE 11: LIVE DEMONSTRATION WORKFLOW
1. **Admin Login:** Authenticate and obtain JWT Bearer token.
2. **Certificate Issuance:** Issue certificate for "Shivakumar", observe on-chain mining, preview and download PDF.
3. **Public ID Verification:** Query certificate ID, verify on-chain confirmation and transaction hash.
4. **QR Code Verification:** Scan generated QR code, auto-populating verification portal.
5. **PDF Upload Verification:** Upload original PDF -> Status: `VERIFIED` (Green), AI Risk: `LOW`.
6. **Tamper Simulation:** Upload modified PDF ("Rahulkumar") -> Status: `TAMPERING_DETECTED` (Red), Diff: `studentName`, AI Risk: `HIGH`.
7. **Certificate Revocation:** Admin revokes certificate -> Status: `REVOKED` on-chain.

## SLIDE 12: DEPLOYMENT ARCHITECTURE
- Multi-stage Dockerfiles for backend (JRE 21) and frontend (Nginx).
- Docker Compose orchestration with internal bridge networking.
- Sepolia testnet deployment configuration.

## SLIDE 13: CONCLUSION & FUTURE SCOPE
- Summary of achievements: secure, scalable, tamper-evident credential ecosystem.
- Future work: Zero-Knowledge Proofs for selective disclosure, W3C Verifiable Credentials, native mobile scanner app.

## SLIDE 14: Q&A / VIVA VOCE
- Open floor for committee questions and technical inspection.
