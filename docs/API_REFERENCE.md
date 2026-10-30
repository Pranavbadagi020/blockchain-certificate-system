# CERTCHAIN REST API SPECIFICATION

Base URL: `http://localhost:8080` (Development) / `https://certchain.institution.edu/api` (Production)

---

## 1. AUTHENTICATION & SESSION MANAGEMENT

### `POST /api/auth/login`
- **Access:** Public
- **Description:** Authenticates user credentials and returns a signed JSON Web Token (JWT).
- **Request Body:**
```json
{
  "email": "admin@certchain.local",
  "password": "PranavAdmin@2024"
}
```
- **Responses:**
  - `200 OK`:
  ```json
  {
    "token": "eyJhbGciOiJIUzUxMiJ9...",
    "email": "admin@certchain.local",
    "name": "System Administrator",
    "role": "ADMIN",
    "institution": "Certificate Verification System",
    "message": "Login successful."
  }
  ```
  - `401 Unauthorized`: Invalid username or password.

### `GET /api/auth/me`
- **Access:** Authenticated (`ISSUER` or `ADMIN`)
- **Headers:** `Authorization: Bearer <token>`
- **Description:** Retrieves current user identity and assigned role.
- **Response (`200 OK`):**
```json
{
  "username": "admin",
  "role": "ADMIN",
  "authenticated": true
}
```

---

## 2. PUBLIC CERTIFICATE VERIFICATION

### `GET /api/certificates/verify/{certificateId}`
- **Access:** Public (No credentials required)
- **Description:** Verifies an academic certificate by its unique alphanumeric identifier against MongoDB and Ethereum.
- **Parameters:** `certificateId` (String, e.g. `CERT-1729428000-8A1F`)
- **Response (`200 OK`):**
```json
{
  "status": "VERIFIED",
  "certificateId": "CERT-1729428000-8A1F",
  "message": "Certificate authenticity verified successfully on blockchain.",
  "isAuthentic": true,
  "onChain": true,
  "certificate": {
    "certificateId": "CERT-1729428000-8A1F",
    "studentName": "Shivakumar",
    "rollNumber": "CS-2024-042",
    "course": "B.Tech Computer Science",
    "grade": "A+",
    "issueDate": "2024-06-15",
    "certificateHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "transactionHash": "0x9e73b412fa0b943d83765e912384a8b75c8790123456789abcdef0123456789a",
    "isRevoked": false
  }
}
```

### `POST /api/certificates/verify/upload`
- **Access:** Public (No credentials required)
- **Description:** Uploads a digital PDF certificate for in-process text extraction, deterministic field comparison, and advisory AI anomaly analysis.
- **Content-Type:** `multipart/form-data`
- **Parameters:** `file` (Multipart file, binary PDF, max 10MB)
- **Response (`200 OK` - Tampering Detected Example):**
```json
{
  "status": "TAMPERING_DETECTED",
  "certificateId": "CERT-1729428000-8A1F",
  "message": "Uploaded PDF has been tampered with or modified after issuance.",
  "isAuthentic": false,
  "onChain": true,
  "certificate": {
    "studentName": "Shivakumar",
    "rollNumber": "CS-2024-042",
    "course": "B.Tech Computer Science",
    "grade": "A+",
    "issueDate": "2024-06-15"
  },
  "fieldMismatches": [
    {
      "field": "studentName",
      "expected": "Shivakumar",
      "extracted": "Rahulkumar"
    }
  ],
  "aiAnalysis": {
    "analysisStatus": "COMPLETED",
    "riskLevel": "HIGH",
    "suspiciousIndicators": [
      "Inconsistent student name: Extracted 'Rahulkumar' diverges from authoritative record 'Shivakumar'",
      "Suspected localized text replacement artifact in student credential block"
    ],
    "explanation": "Critical discrepancy identified between authoritative blockchain-anchored registration and extracted document content.",
    "recommendedAction": "Reject document immediately. Report potential academic credential forgery to university registrar.",
    "modelOrMethod": "Heuristic Rule-Based Anomaly Detector v1.0",
    "disclaimer": "AI-Assisted Analysis supplements, but does not replace, cryptographic verification."
  }
}
```

---

## 3. CERTIFICATE ISSUANCE & REVOCATION

### `POST /api/certificates/issue`
- **Access:** Authenticated (`ISSUER` or `ADMIN`)
- **Headers:** `Authorization: Bearer <token>`
- **Description:** Computes canonical SHA-256 hash, issues certificate on-chain, and persists metadata to MongoDB.
- **Request Body:**
```json
{
  "studentName": "Ananya Sharma",
  "rollNumber": "CS-2024-015",
  "course": "B.Tech Computer Science",
  "grade": "A+",
  "issueDate": "2024-06-15"
}
```
- **Response (`200 OK`):**
```json
{
  "id": "6714ea8b1234567890abcdef",
  "certificateId": "CERT-1729428000-4B9C",
  "studentName": "Ananya Sharma",
  "rollNumber": "CS-2024-015",
  "course": "B.Tech Computer Science",
  "grade": "A+",
  "issueDate": "2024-06-15",
  "certificateHash": "3a4f6d8c...",
  "transactionHash": "0x5c8e...",
  "isRevoked": false
}
```

### `POST /api/certificates/revoke/{certificateId}`
- **Access:** Authenticated (`ADMIN` only)
- **Headers:** `Authorization: Bearer <token>`
- **Description:** Revokes certificate on-chain and updates MongoDB status.
- **Request Body:**
```json
{
  "reason": "Administrative cancellation due to academic dishonesty"
}
```
- **Response (`200 OK`):**
```json
{
  "certificateId": "CERT-1729428000-4B9C",
  "isRevoked": true,
  "revocationReason": "Administrative cancellation due to academic dishonesty",
  "revocationTransactionHash": "0x8b2e..."
}
```

---

## 4. ADMINISTRATIVE GOVERNANCE

### `GET /api/admin/metrics`
- **Access:** Authenticated (`ADMIN` only)
- **Response (`200 OK`):**
```json
{
  "totalIssued": 128,
  "validCertificates": 124,
  "revokedCertificates": 4,
  "verificationAttempts": 342,
  "tamperingDetections": 7,
  "totalIssuers": 3
}
```

### `GET /api/admin/certificates`
- **Access:** Authenticated (`ADMIN` only)
- **Query Parameters:** `page` (int, default 0), `size` (int, default 10), `search` (string), `status` (`ALL` | `VALID` | `REVOKED`)
- **Response (`200 OK`):** Paginated certificate records with total elements and page count.

### `GET /api/admin/audit-logs`
- **Access:** Authenticated (`ADMIN` only)
- **Query Parameters:** `limit` (int, default 50)
- **Response (`200 OK`):** Array of recent security and audit log entries.

### `POST /api/admin/issuers`
- **Access:** Authenticated (`ADMIN` only)
- **Request Body:**
```json
{
  "name": "Department of ECE",
  "email": "dept_ece@institution.edu",
  "password": "SecurePassword@123",
  "institution": "State Technical University"
}
```
- **Response (`201 Created`):**
```json
{
  "id": "6714eb990abcdef123456789",
  "name": "Department of ECE",
  "email": "dept_ece@institution.edu",
  "role": "ISSUER",
  "institution": "State Technical University",
  "message": "Issuer registered successfully."
}
```
