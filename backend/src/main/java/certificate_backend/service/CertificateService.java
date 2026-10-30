package certificate_backend.service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Year;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import certificate_backend.dto.AiAnalysisResult;
import certificate_backend.dto.CertificateResponse;
import certificate_backend.dto.FieldMismatch;
import certificate_backend.dto.IssueCertificateRequest;
import certificate_backend.dto.VerifyResponse;
import certificate_backend.exception.CertificateNotFoundException;
import certificate_backend.exception.DuplicateCertificateException;
import certificate_backend.model.Certificate;
import certificate_backend.model.CertificateStatus;
import certificate_backend.repository.CertificateRepository;

import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CertificateService {

    private static final Logger log =
            LoggerFactory.getLogger(CertificateService.class);

    static final String HASH_SEPARATOR = "|";

    private final AtomicInteger sequence = new AtomicInteger(
            (int) (System.currentTimeMillis() % 90000) + 10000
    );

    private final CertificateRepository repository;
    private final BlockchainService blockchainService;
    private final AuditService auditService;
    private final AiAnalysisService aiAnalysisService;

    public CertificateService(
            CertificateRepository repository,
            BlockchainService blockchainService) {
        this(repository, blockchainService, null, null);
    }

    @Autowired
    public CertificateService(
            CertificateRepository repository,
            BlockchainService blockchainService,
            @Autowired(required = false) AuditService auditService,
            @Autowired(required = false) AiAnalysisService aiAnalysisService) {
        this.repository = repository;
        this.blockchainService = blockchainService;
        this.auditService = auditService;
        this.aiAnalysisService = aiAnalysisService;
    }

    /**
     * Retrieve all certificates stored in MongoDB.
     */
    public List<CertificateResponse> getAllCertificates() {
        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Issue a certificate and save it to MongoDB after recording on the blockchain.
     */
    public CertificateResponse issueCertificate(
            IssueCertificateRequest request) {

        String certId = generateCertificateId();

        if (repository.existsByCertificateId(certId)) {
            throw new DuplicateCertificateException(certId);
        }

        String studentName = request.getStudentName().trim();
        String studentRollNumber = request.getStudentRollNumber().trim();
        String course = request.getCourse().trim();
        String institution = request.getInstitution().trim();
        String grade = request.getGrade() != null
                ? request.getGrade().trim()
                : "";
        String issueDate = request.getIssueDate().trim();

        String hash = computeHash(
                studentName,
                studentRollNumber,
                course,
                institution,
                grade,
                issueDate
        );

        String txHash;
        try {
            txHash = blockchainService.issueCertificate(certId, hash);
        } catch (Exception e) {
            log.error(
                    "Blockchain issuance failed for certificateId={}: {}",
                    certId,
                    e.getMessage(),
                    e
            );
            throw new IllegalStateException(
                    "Blockchain certificate issuance failed: " + e.getMessage(),
                    e
            );
        }

        Certificate cert = Certificate.builder()
                .certificateId(certId)
                .studentName(studentName)
                .studentRollNumber(studentRollNumber)
                .course(course)
                .institution(institution)
                .grade(grade)
                .issueDate(issueDate)
                .certificateHash(hash)
                .transactionHash(txHash)
                .status(CertificateStatus.VALID)
                .build();

        Certificate saved;
        try {
            saved = repository.save(cert);
        } catch (Exception mongoEx) {
            log.error(
                    "CRITICAL: Failed to save certificate to MongoDB after successful blockchain issuance! "
                            + "certificateId={}, transactionHash={}, certificateHash={}",
                    certId,
                    txHash,
                    hash,
                    mongoEx
            );

            try {
                log.info(
                        "Attempting compensating on-chain revocation for certificateId={}",
                        certId
                );
                blockchainService.revokeCertificate(certId);
                log.info(
                        "Compensating on-chain revocation SUCCEEDED for certificateId={}",
                        certId
                );
            } catch (Exception revokeEx) {
                log.error(
                        "CRITICAL: Compensating on-chain revocation FAILED for certificateId={}: {}",
                        certId,
                        revokeEx.getMessage(),
                        revokeEx
                );
            }

            throw new IllegalStateException(
                    "Failed to save certificate to database after blockchain issuance.",
                    mongoEx
            );
        }

        log.info(
                "Certificate issued: id={}, hash={}, txHash={}",
                certId,
                hash,
                txHash
        );

        if (auditService != null) {
            auditService.logEvent(
                "CERT_ISSUED",
                "SYSTEM",
                null,
                "SUCCESS",
                Map.of(
                    "certificateId", certId,
                    "studentName", request.getStudentName(),
                    "rollNumber", request.getStudentRollNumber(),
                    "transactionHash", txHash != null ? txHash : ""
                )
            );
        }

        return toResponse(saved);
    }

    /**
     * Verify a certificate using its stored data, local SHA-256 hash, and the blockchain registry.
     */
    public VerifyResponse verifyCertificate(String certificateId) {

        Certificate cert = repository.findByCertificateId(certificateId)
                .orElseThrow(
                        () -> new CertificateNotFoundException(certificateId)
                );

        String recomputedHash = computeHash(
                cert.getStudentName(),
                cert.getStudentRollNumber(),
                cert.getCourse(),
                cert.getInstitution(),
                cert.getGrade() != null ? cert.getGrade() : "",
                cert.getIssueDate()
        );

        boolean dbHashMatches =
                recomputedHash.equalsIgnoreCase(cert.getCertificateHash());

        if (!dbHashMatches) {
            log.warn(
                    "TAMPERED certificate detected in database: id={}",
                    certificateId
            );

            return finalizeIdVerify(VerifyResponse.builder()
                    .verificationResult("TAMPERED")
                    .message("Certificate data does not match its recorded hash. "
                            + "This certificate may have been modified after issuance.")
                    .certificate(toResponse(cert))
                    .blockchainVerified(false)
                    .blockchainNote(
                            "Blockchain verification was skipped because database data integrity check failed."
                    )
                    .build(), certificateId);
        }

        BlockchainService.CertificateChainDetails chainDetails;
        try {
            chainDetails = blockchainService.getCertificate(certificateId);
        } catch (Exception rpcEx) {
            log.warn(
                    "Blockchain unavailable during verification of certificate id={}: {}",
                    certificateId,
                    rpcEx.getMessage()
            );

            if (cert.getStatus() == CertificateStatus.REVOKED) {
                return finalizeIdVerify(VerifyResponse.builder()
                        .verificationResult("REVOKED")
                        .message("This certificate has been revoked by the issuing institution.")
                        .certificate(toResponse(cert))
                        .blockchainVerified(false)
                        .blockchainNote(
                                "Certificate is marked as revoked in database. Blockchain was unreachable: "
                                        + rpcEx.getMessage()
                        )
                        .build(), certificateId);
            }

            return finalizeIdVerify(VerifyResponse.builder()
                    .verificationResult("BLOCKCHAIN_UNAVAILABLE")
                    .message("Certificate data is intact in the database, but the blockchain is currently unreachable. "
                            + "Authenticity could not be confirmed on-chain.")
                    .certificate(toResponse(cert))
                    .blockchainVerified(false)
                    .blockchainNote("Unable to reach the blockchain: " + rpcEx.getMessage())
                    .build(), certificateId);
        }

        boolean existsOnChain = chainDetails.issuedAt() != null
                && chainDetails.issuedAt().compareTo(BigInteger.ZERO) > 0;

        if (!existsOnChain) {
            log.warn(
                    "Certificate id={} not found on blockchain (issuedAt=0)",
                    certificateId
            );

            return finalizeIdVerify(VerifyResponse.builder()
                    .verificationResult("NOT_FOUND")
                    .message("Certificate record was not found on the blockchain registry.")
                    .certificate(toResponse(cert))
                    .blockchainVerified(false)
                    .blockchainNote(
                            "The certificate exists in the database but has no registered record on the blockchain."
                    )
                    .build(), certificateId);
        }

        String onChainHash = normalizeHash(chainDetails.certificateHash());
        String localHash = normalizeHash(recomputedHash);

        if (!onChainHash.equalsIgnoreCase(localHash)) {
            log.warn(
                    "TAMPERED certificate detected on blockchain: id={}, onChainHash={}, localHash={}",
                    certificateId,
                    onChainHash,
                    localHash
            );

            return finalizeIdVerify(VerifyResponse.builder()
                    .verificationResult("TAMPERED")
                    .message("Certificate data does not match the hash recorded on the blockchain. "
                            + "This certificate may have been modified or falsified.")
                    .certificate(toResponse(cert))
                    .blockchainVerified(false)
                    .blockchainNote(
                            "Blockchain hash mismatch: on-chain hash does not match computed certificate hash."
                    )
                    .build(), certificateId);
        }

        if (cert.getStatus() == CertificateStatus.REVOKED || chainDetails.revoked()) {
            log.info(
                    "Certificate verified as REVOKED: id={}, dbRevoked={}, chainRevoked={}",
                    certificateId,
                    cert.getStatus() == CertificateStatus.REVOKED,
                    chainDetails.revoked()
            );

            String revocationNote = chainDetails.revoked()
                    ? "Certificate authenticity verified on-chain, and recorded as revoked on the blockchain."
                    : "Certificate verified on-chain, but marked as revoked in the database.";

            return finalizeIdVerify(VerifyResponse.builder()
                    .verificationResult("REVOKED")
                    .message("This certificate has been revoked by the issuing institution.")
                    .certificate(toResponse(cert))
                    .blockchainVerified(true)
                    .blockchainNote(revocationNote)
                    .build(), certificateId);
        }

        log.debug(
                "Certificate verified: id={}, result=VALID",
                certificateId
        );

        return finalizeIdVerify(VerifyResponse.builder()
                .verificationResult("VALID")
                .message("Certificate is authentic and has not been modified.")
                .certificate(toResponse(cert))
                .blockchainVerified(true)
                .blockchainNote(
                        "Certificate successfully verified against the Ethereum blockchain registry."
                )
                .build(), certificateId);
    }

    private String normalizeHash(String hash) {
        if (hash == null) {
            return "";
        }
        String trimmed = hash.trim();
        if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
            return trimmed.substring(2);
        }
        return trimmed;
    }

    // ── Blockchain Re-registration ────────────────────────────────────────────

    /**
     * Re-registers a single certificate onto the blockchain.
     *
     * <p>This is needed when the Hardhat local node is restarted (wiping its
     * in-memory state) but MongoDB still holds the certificate records. The
     * certificate data is unchanged — we simply resubmit the existing hash.
     *
     * <p>The method checks whether the certificate already exists on-chain before
     * sending the transaction, so it is safe to call on a certificate that is
     * already registered (it returns the existing tx hash).
     *
     * @param certificateId the certificate to re-register
     * @return updated CertificateResponse with the new transaction hash
     */
    public CertificateResponse reRegisterToBlockchain(String certificateId) {

        Certificate cert = repository.findByCertificateId(certificateId)
                .orElseThrow(() -> new CertificateNotFoundException(certificateId));

        if (cert.getStatus() == CertificateStatus.REVOKED) {
            throw new IllegalStateException(
                    "Certificate " + certificateId + " is REVOKED and cannot be re-registered.");
        }

        // Verify the stored hash is still consistent with the stored fields
        String recomputedHash = computeHash(
                cert.getStudentName(),
                cert.getStudentRollNumber(),
                cert.getCourse(),
                cert.getInstitution(),
                cert.getGrade() != null ? cert.getGrade() : "",
                cert.getIssueDate()
        );

        if (!recomputedHash.equalsIgnoreCase(cert.getCertificateHash())) {
            throw new IllegalStateException(
                    "Certificate " + certificateId
                    + " has a hash inconsistency in MongoDB. Cannot re-register.");
        }

        // Check if it is already on-chain (idempotency guard)
        try {
            BlockchainService.CertificateChainDetails existing =
                    blockchainService.getCertificate(certificateId);
            if (existing.issuedAt() != null
                    && existing.issuedAt().compareTo(java.math.BigInteger.ZERO) > 0) {
                log.info("Certificate {} is already on-chain. No re-registration needed.", certificateId);
                return toResponse(cert);
            }
        } catch (Exception checkEx) {
            log.warn("Could not check on-chain status before re-registration: {}", checkEx.getMessage());
            // Proceed with re-registration attempt
        }

        String newTxHash;
        try {
            newTxHash = blockchainService.issueCertificate(certificateId, cert.getCertificateHash());
        } catch (Exception e) {
            log.error("Blockchain re-registration failed for certificateId={}: {}", certificateId, e.getMessage(), e);
            throw new IllegalStateException(
                    "Blockchain re-registration failed: " + e.getMessage(), e);
        }

        cert.setTransactionHash(newTxHash);
        Certificate saved = repository.save(cert);

        log.info("Certificate {} re-registered on blockchain. New txHash={}", certificateId, newTxHash);
        return toResponse(saved);
    }

    /**
     * Re-registers ALL certificates in MongoDB that are not currently found on-chain.
     * Useful after a Hardhat restart.
     *
     * @return list of results for each certificate attempted
     */
    public java.util.List<java.util.Map<String, Object>> reRegisterAllToBlockchain() {
        java.util.List<Certificate> all = repository.findAll();
        java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();

        for (Certificate cert : all) {
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("certificateId", cert.getCertificateId());
            result.put("studentName", cert.getStudentName());

            if (cert.getStatus() == CertificateStatus.REVOKED) {
                result.put("status", "SKIPPED");
                result.put("reason", "Certificate is REVOKED");
                results.add(result);
                continue;
            }

            try {
                // Check if already on-chain
                BlockchainService.CertificateChainDetails existing =
                        blockchainService.getCertificate(cert.getCertificateId());
                if (existing.issuedAt() != null
                        && existing.issuedAt().compareTo(java.math.BigInteger.ZERO) > 0) {
                    result.put("status", "ALREADY_ON_CHAIN");
                    result.put("reason", "Certificate already registered on blockchain");
                    results.add(result);
                    continue;
                }
            } catch (Exception checkEx) {
                log.warn("Could not check on-chain status for {}: {}", cert.getCertificateId(), checkEx.getMessage());
            }

            try {
                String newTxHash = blockchainService.issueCertificate(
                        cert.getCertificateId(), cert.getCertificateHash());
                cert.setTransactionHash(newTxHash);
                repository.save(cert);
                result.put("status", "RE_REGISTERED");
                result.put("transactionHash", newTxHash);
                log.info("Re-registered {} → txHash={}", cert.getCertificateId(), newTxHash);
            } catch (Exception e) {
                result.put("status", "FAILED");
                result.put("reason", e.getMessage());
                log.error("Re-registration failed for {}: {}", cert.getCertificateId(), e.getMessage());
            }

            results.add(result);
        }

        return results;
    }

    /**
     * Revoke a certificate on-chain and in MongoDB while preserving its record for auditing.
     */
    public CertificateResponse revokeCertificate(String certificateId) {

        Certificate cert = repository.findByCertificateId(certificateId)
                .orElseThrow(
                        () -> new CertificateNotFoundException(certificateId)
                );

        if (cert.getStatus() == CertificateStatus.REVOKED) {
            log.info(
                    "Certificate is already revoked: id={}",
                    certificateId
            );

            return toResponse(cert);
        }

        String txHash;
        try {
            txHash = blockchainService.revokeCertificate(certificateId);
        } catch (Exception e) {
            log.error(
                    "Blockchain revocation failed for certificateId={}: {}",
                    certificateId,
                    e.getMessage(),
                    e
            );
            throw new IllegalStateException(
                    "Blockchain certificate revocation failed: " + e.getMessage(),
                    e
            );
        }

        cert.setStatus(CertificateStatus.REVOKED);

        Certificate saved;
        try {
            saved = repository.save(cert);
        } catch (Exception mongoEx) {
            log.error(
                    "CRITICAL CONSISTENCY RISK: On-chain revocation succeeded (txHash={}) for certificateId={}, "
                            + "but updating MongoDB status to REVOKED failed! The certificate is now permanently revoked on-chain "
                            + "but remains marked as VALID in MongoDB. Manual database reconciliation is required.",
                    txHash,
                    certificateId,
                    mongoEx
            );
            throw new IllegalStateException(
                    "Certificate revoked on blockchain (txHash=" + txHash + "), but failed to update database status.",
                    mongoEx
            );
        }

        log.info(
                "Certificate successfully revoked: id={}, txHash={}",
                certificateId,
                txHash
        );

        if (auditService != null) {
            auditService.logEvent(
                "CERT_REVOKED",
                "ADMIN",
                null,
                "SUCCESS",
                Map.of(
                    "certificateId", certificateId,
                    "transactionHash", txHash != null ? txHash : ""
                )
            );
        }

        return toResponse(saved);
    }

    /**
     * Compute the SHA-256 hash for a certificate's immutable fields.
     */
    String computeHash(
            String studentName,
            String studentRollNumber,
            String course,
            String institution,
            String grade,
            String issueDate) {

        String canonical = String.join(
                HASH_SEPARATOR,
                studentName,
                studentRollNumber,
                course,
                institution,
                grade,
                issueDate
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = digest.digest(
                    canonical.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 algorithm not available",
                    e
            );
        }
    }

    /**
     * Generate a certificate ID in the format CERT-YYYY-NNNNN.
     */
    private String generateCertificateId() {
        int year = Year.now().getValue();
        int seq = sequence.getAndIncrement();

        return String.format("CERT-%d-%05d", year, seq);
    }

    /**
     * Convert a certificate document to its response DTO.
     */
    private CertificateResponse toResponse(Certificate cert) {

        return CertificateResponse.builder()
                .certificateId(cert.getCertificateId())
                .studentName(cert.getStudentName())
                .studentRollNumber(cert.getStudentRollNumber())
                .course(cert.getCourse())
                .institution(cert.getInstitution())
                .grade(cert.getGrade())
                .issueDate(cert.getIssueDate())
                .certificateHash(cert.getCertificateHash())
                .transactionHash(cert.getTransactionHash())
                .status(cert.getStatus())
                .createdAt(cert.getCreatedAt())
                .build();
    }

    /**
     * Verify an uploaded PDF certificate against authoritative MongoDB records and the blockchain.
     * Extracts fields using Apache PDFBox, detects any field or hash modifications, and returns
     * VERIFIED, TAMPERING_DETECTED, REVOKED, NOT_FOUND, UNABLE_TO_VERIFY, or NEEDS_REVIEW.
     */
    public VerifyResponse verifyCertificatePdf(byte[] pdfBytes) {
        String pdfText = "";
        Map<String, String> extracted = null;
        Optional<Certificate> optCert = Optional.empty();
        List<FieldMismatch> mismatches = new ArrayList<>();

        if (pdfBytes == null || pdfBytes.length == 0) {
            return enrichWithAiAndAudit(VerifyResponse.builder()
                    .verificationResult("UNABLE_TO_VERIFY")
                    .message("The uploaded file is empty.")
                    .blockchainVerified(false)
                    .build(), pdfText, extracted, optCert.orElse(null), mismatches);
        }

        // Enforce 10 MB maximum limit
        if (pdfBytes.length > 10 * 1024 * 1024) {
            return enrichWithAiAndAudit(VerifyResponse.builder()
                    .verificationResult("UNABLE_TO_VERIFY")
                    .message("Uploaded file exceeds the maximum allowed size of 10 MB.")
                    .blockchainVerified(false)
                    .build(), pdfText, extracted, optCert.orElse(null), mismatches);
        }

        // Validate PDF magic bytes (%PDF-)
        if (!isPdfMagicBytes(pdfBytes)) {
            return enrichWithAiAndAudit(VerifyResponse.builder()
                    .verificationResult("UNABLE_TO_VERIFY")
                    .message("The uploaded file is not a valid PDF document (missing PDF magic header).")
                    .blockchainVerified(false)
                    .build(), pdfText, extracted, optCert.orElse(null), mismatches);
        }

        String rawFileHash = computeFileSha256(pdfBytes);

        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes))) {
            if (document.isEncrypted()) {
                log.warn("Uploaded PDF is encrypted or password-protected");
                return enrichWithAiAndAudit(VerifyResponse.builder()
                        .verificationResult("UNABLE_TO_VERIFY")
                        .message("The uploaded PDF is encrypted or password-protected and cannot be verified.")
                        .uploadedFileHash(rawFileHash)
                        .blockchainVerified(false)
                        .build(), pdfText, extracted, optCert.orElse(null), mismatches);
            }

            PDFTextStripper stripper = new PDFTextStripper();
            pdfText = stripper.getText(document);
        } catch (Exception e) {
            log.warn("Failed to parse uploaded PDF: {}", e.getMessage());
            return enrichWithAiAndAudit(VerifyResponse.builder()
                    .verificationResult("UNABLE_TO_VERIFY")
                    .message("The uploaded PDF is corrupted, malformed, or could not be parsed: " + e.getMessage())
                    .uploadedFileHash(rawFileHash)
                    .blockchainVerified(false)
                    .build(), pdfText, extracted, optCert.orElse(null), mismatches);
        }

        if (pdfText == null || pdfText.trim().isEmpty()) {
            return enrichWithAiAndAudit(VerifyResponse.builder()
                    .verificationResult("UNABLE_TO_VERIFY")
                    .message("The uploaded PDF does not contain extractable text.")
                    .uploadedFileHash(rawFileHash)
                    .blockchainVerified(false)
                    .build(), pdfText, extracted, optCert.orElse(null), mismatches);
        }

        extracted = extractCertificateFields(pdfText);
        String certId = extracted.get("certificateId");

        // External issuer detection (Infosys Springboard, Coursera, etc.)
        if (certId == null || certId.isBlank()) {
            if (isExternalCertificate(pdfText)) {
                String externalIssuer = detectExternalIssuer(pdfText);
                log.info("External third-party certificate detected: {}", externalIssuer);
                return enrichWithAiAndAudit(VerifyResponse.builder()
                        .verificationResult("NEEDS_REVIEW")
                        .message("External certificate detected (" + externalIssuer + "). "
                                + "CertChain does not maintain authoritative on-chain cryptographic registry records for third-party issuers. "
                                + "Please verify this credential directly through the issuer's official portal.")
                        .extractedDetails(extracted)
                        .uploadedFileHash(rawFileHash)
                        .blockchainVerified(false)
                        .blockchainNote("External issuer certificate cannot be verified against the local CertChain blockchain registry. Direct issuer portal verification is required.")
                        .build(), pdfText, extracted, null, mismatches);
            }

            return enrichWithAiAndAudit(VerifyResponse.builder()
                    .verificationResult("UNABLE_TO_VERIFY")
                    .message("Could not identify a valid Certificate ID in the uploaded document.")
                    .uploadedFileHash(rawFileHash)
                    .blockchainVerified(false)
                    .build(), pdfText, extracted, optCert.orElse(null), mismatches);
        }

        // Authoritative MongoDB record lookup
        optCert = repository.findByCertificateId(certId);
        if (optCert.isEmpty()) {
            // Check if this missing cert is actually an external certificate with an external ID
            if (isExternalCertificate(pdfText)) {
                String externalIssuer = detectExternalIssuer(pdfText);
                return enrichWithAiAndAudit(VerifyResponse.builder()
                        .verificationResult("NEEDS_REVIEW")
                        .message("External certificate detected (" + externalIssuer + "). "
                                + "CertChain does not maintain authoritative on-chain cryptographic registry records for third-party issuers. "
                                + "Please verify this credential directly through the issuer's official portal.")
                        .extractedDetails(extracted)
                        .uploadedFileHash(rawFileHash)
                        .blockchainVerified(false)
                        .blockchainNote("External issuer certificate cannot be verified against the local CertChain blockchain registry. Direct issuer portal verification is required.")
                        .build(), pdfText, extracted, null, mismatches);
            }

            log.info("Certificate ID {} extracted from PDF was not found in registry", certId);
            return enrichWithAiAndAudit(VerifyResponse.builder()
                    .verificationResult("NOT_FOUND")
                    .message("Certificate ID " + certId + " was not found in the institution registry.")
                    .extractedDetails(extracted)
                    .uploadedFileHash(rawFileHash)
                    .blockchainVerified(false)
                    .blockchainNote("The certificate ID was not found in the institutional database or blockchain registry.")
                    .build(), pdfText, extracted, optCert.orElse(null), mismatches);
        }

        Certificate cert = optCert.get();
        CertificateResponse certResp = toResponse(cert);

        // Check database revocation status
        if (cert.getStatus() == CertificateStatus.REVOKED) {
            log.info("Uploaded certificate is marked as REVOKED: id={}", certId);
            return enrichWithAiAndAudit(VerifyResponse.builder()
                    .verificationResult("REVOKED")
                    .message("This certificate has been revoked by the issuing institution.")
                    .certificate(certResp)
                    .extractedDetails(extracted)
                    .uploadedFileHash(rawFileHash)
                    .blockchainVerified(true)
                    .blockchainNote("Certificate authenticity recorded, but certificate is marked as revoked in database.")
                    .build(), pdfText, extracted, cert, mismatches);
        }

        // Check on-chain blockchain status
        BlockchainService.CertificateChainDetails chainDetails;
        try {
            chainDetails = blockchainService.getCertificate(certId);
        } catch (Exception rpcEx) {
            log.warn("Blockchain unavailable during PDF verification of id={}: {}", certId, rpcEx.getMessage());
            return enrichWithAiAndAudit(VerifyResponse.builder()
                    .verificationResult("UNABLE_TO_VERIFY")
                    .message("Certificate data was extracted and matches database records, but the blockchain registry is currently unreachable.")
                    .certificate(certResp)
                    .extractedDetails(extracted)
                    .uploadedFileHash(rawFileHash)
                    .blockchainVerified(false)
                    .blockchainNote("Blockchain unreachable: " + rpcEx.getMessage())
                    .build(), pdfText, extracted, cert, mismatches);
        }

        boolean existsOnChain = chainDetails.issuedAt() != null
                && chainDetails.issuedAt().compareTo(BigInteger.ZERO) > 0;

        if (!existsOnChain) {
            log.warn("Certificate id={} not found on blockchain during PDF verification", certId);
            return enrichWithAiAndAudit(VerifyResponse.builder()
                    .verificationResult("NOT_FOUND")
                    .message("Certificate record was not found on the blockchain registry.")
                    .certificate(certResp)
                    .extractedDetails(extracted)
                    .uploadedFileHash(rawFileHash)
                    .blockchainVerified(false)
                    .blockchainNote("The certificate exists in the database but has no registered record on the blockchain.")
                    .build(), pdfText, extracted, cert, mismatches);
        }

        if (chainDetails.revoked()) {
            log.info("Certificate verified as REVOKED on blockchain: id={}", certId);
            return enrichWithAiAndAudit(VerifyResponse.builder()
                    .verificationResult("REVOKED")
                    .message("This certificate has been revoked on the blockchain registry.")
                    .certificate(certResp)
                    .extractedDetails(extracted)
                    .uploadedFileHash(rawFileHash)
                    .blockchainVerified(true)
                    .blockchainNote("Certificate is recorded as revoked on the blockchain.")
                    .build(), pdfText, extracted, cert, mismatches);
        }

        // Field-by-field comparison against authoritative MongoDB record
        checkFieldMatch("studentName", cert.getStudentName(), extracted.get("studentName"), mismatches);
        checkFieldMatch("studentRollNumber", cert.getStudentRollNumber(), extracted.get("studentRollNumber"), mismatches);
        checkFieldMatch("course", cert.getCourse(), extracted.get("course"), mismatches);
        checkInstitutionMatch(cert.getInstitution(), extracted.get("institution"), mismatches);
        checkFieldMatch("grade", cert.getGrade() != null ? cert.getGrade() : "", extracted.get("grade"), mismatches);
        checkFieldMatch("issueDate", cert.getIssueDate(), extracted.get("issueDate"), mismatches);

        // Check on-chain hash vs database hash
        String onChainHash = normalizeHash(chainDetails.certificateHash());
        String dbHash = normalizeHash(cert.getCertificateHash());
        if (!onChainHash.equalsIgnoreCase(dbHash)) {
            mismatches.add(FieldMismatch.builder()
                    .field("blockchainHash")
                    .expected(onChainHash)
                    .found(dbHash)
                    .message("Database certificate hash does not match the on-chain hash.")
                    .build());
        }

        // Check extracted document's printed hash if present
        String printedHash = extracted.get("certificateHash");
        if (printedHash != null && !printedHash.isBlank()) {
            String normPrinted = normalizeHash(printedHash);
            if (!normPrinted.equalsIgnoreCase(dbHash)) {
                mismatches.add(FieldMismatch.builder()
                        .field("printedHash")
                        .expected(dbHash)
                        .found(normPrinted)
                        .message("The content hash printed on the document does not match the registered hash.")
                        .build());
            }
        }

        // Recompute canonical hash from extracted fields
        String extractedName = extracted.getOrDefault("studentName", "");
        String extractedRoll = extracted.getOrDefault("studentRollNumber", "");
        String extractedCourse = extracted.getOrDefault("course", "");
        String extractedInst = cert.getInstitution();
        String extractedGrade = extracted.getOrDefault("grade", "");
        String extractedDate = extracted.getOrDefault("issueDate", "");

        String recomputedFromExtracted = computeHash(
                extractedName,
                extractedRoll,
                extractedCourse,
                extractedInst,
                extractedGrade,
                extractedDate
        );

        if (!recomputedFromExtracted.equalsIgnoreCase(dbHash)) {
            if (mismatches.stream().noneMatch(m -> "certificateHash".equals(m.getField()))) {
                mismatches.add(FieldMismatch.builder()
                        .field("certificateHash")
                        .expected(dbHash)
                        .found(recomputedFromExtracted)
                        .message("The cryptographic hash calculated from the extracted fields does not match the registered certificate hash.")
                        .build());
            }
        }

        if (!mismatches.isEmpty()) {
            log.warn("Tampering detected for certificate {}: {} mismatch(es)", certId, mismatches.size());
            return enrichWithAiAndAudit(VerifyResponse.builder()
                    .verificationResult("TAMPERING_DETECTED")
                    .message("Tampering detected: the uploaded certificate contents do not match the authoritative blockchain registry records.")
                    .certificate(certResp)
                    .mismatchDetails(mismatches)
                    .extractedDetails(extracted)
                    .uploadedFileHash(rawFileHash)
                    .blockchainVerified(false)
                    .blockchainNote("Verification failed: document details or cryptographic hash mismatch.")
                    .build(), pdfText, extracted, cert, mismatches);
        }

        log.info("Certificate {} successfully verified from uploaded PDF", certId);
        return enrichWithAiAndAudit(VerifyResponse.builder()
                .verificationResult("VERIFIED")
                .message("Certificate verified: the uploaded document matches the authentic blockchain record.")
                .certificate(certResp)
                .extractedDetails(extracted)
                .uploadedFileHash(rawFileHash)
                .blockchainVerified(true)
                .blockchainNote("Certificate successfully verified against the Ethereum blockchain registry.")
                .build(), pdfText, extracted, cert, mismatches);
    }

    private boolean isExternalCertificate(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("infosys springboard")
                || lower.contains("infosys")
                || lower.contains("springboard")
                || lower.contains("coursera")
                || lower.contains("edx")
                || lower.contains("udemy")
                || lower.contains("udacity")
                || lower.contains("linkedin learning");
    }

    private String detectExternalIssuer(String text) {
        if (text == null) return "External Issuer";
        String lower = text.toLowerCase();
        if (lower.contains("infosys springboard") || lower.contains("springboard")) return "Infosys Springboard";
        if (lower.contains("infosys")) return "Infosys";
        if (lower.contains("coursera")) return "Coursera";
        if (lower.contains("edx")) return "edX";
        if (lower.contains("udemy")) return "Udemy";
        if (lower.contains("udacity")) return "Udacity";
        if (lower.contains("linkedin learning")) return "LinkedIn Learning";
        return "Third-Party External Issuer";
    }

    private void checkFieldMatch(
            String fieldName,
            String expected,
            String found,
            List<FieldMismatch> mismatches) {

        String expNorm = expected != null ? expected.trim() : "";
        String fndNorm = found != null ? found.trim() : "";

        if (!expNorm.equalsIgnoreCase(fndNorm)) {
            mismatches.add(FieldMismatch.builder()
                    .field(fieldName)
                    .expected(expNorm)
                    .found(fndNorm.isEmpty() ? "(not found or empty)" : fndNorm)
                    .message(String.format("Mismatch in %s: expected '%s', but found '%s'", fieldName, expNorm, fndNorm))
                    .build());
        }
    }

    private void checkInstitutionMatch(
            String expected,
            String found,
            List<FieldMismatch> mismatches) {

        String expNorm = expected != null ? expected.trim().toUpperCase() : "";
        String fndNorm = found != null ? found.trim().toUpperCase() : "";

        boolean matches = expNorm.equals(fndNorm)
                || fndNorm.contains(expNorm)
                || expNorm.contains(fndNorm);

        if (!matches) {
            mismatches.add(FieldMismatch.builder()
                    .field("institution")
                    .expected(expected != null ? expected.trim() : "")
                    .found(found != null ? found.trim() : "(not found)")
                    .message(String.format("Mismatch in institution: expected '%s', but found '%s'", expected, found))
                    .build());
        }
    }

    private boolean isPdfMagicBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 5) return false;
        // %PDF-
        return bytes[0] == 0x25 && bytes[1] == 0x50 && bytes[2] == 0x44 && bytes[3] == 0x46 && bytes[4] == 0x2D;
    }

    private String computeFileSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(bytes);
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    Map<String, String> extractCertificateFields(String text) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (text == null) return fields;

        // 1. Certificate ID
        Matcher mId = Pattern.compile("OFFICIAL CERTIFICATE ID\\s*\\R\\s*([A-Za-z0-9_-]+)").matcher(text);
        if (mId.find()) {
            fields.put("certificateId", mId.group(1).trim());
        } else {
            Matcher mId2 = Pattern.compile("(CERT-\\d{4}-\\d{5})").matcher(text);
            if (mId2.find()) {
                fields.put("certificateId", mId2.group(1).trim());
            }
        }

        // 2. Student Name
        Matcher mName = Pattern.compile("This is to certify that\\s*\\R\\s*([^\\r\\n]+?)\\s*\\R\\s*Institutional ID").matcher(text);
        if (mName.find()) {
            fields.put("studentName", mName.group(1).trim());
        }

        // 3. Roll Number
        Matcher mRoll = Pattern.compile("Institutional ID / Roll Number:\\s*([^\\r\\n]+)").matcher(text);
        if (mRoll.find()) {
            fields.put("studentRollNumber", mRoll.group(1).trim());
        }

        // 4. Course
        Matcher mCourse = Pattern.compile("completed the prescribed programme in\\s*\\R\\s*(.*?)\\s*\\R\\s*(?:Grade / Classification:|Conferred on:)", Pattern.DOTALL).matcher(text);
        if (mCourse.find()) {
            String course = mCourse.group(1).replaceAll("\\R", " ").replaceAll("\\s+", " ").trim();
            fields.put("course", course);
        }

        // 5. Grade
        Matcher mGrade = Pattern.compile("Grade / Classification:\\s*([^\\r\\n]+)").matcher(text);
        if (mGrade.find()) {
            fields.put("grade", mGrade.group(1).trim());
        } else {
            fields.put("grade", "");
        }

        // 6. Issue Date
        Matcher mDate = Pattern.compile("Conferred on:\\s*([^\\r\\n]+)").matcher(text);
        if (mDate.find()) {
            fields.put("issueDate", mDate.group(1).trim());
        }

        // 7. Content Hash
        Matcher mHash = Pattern.compile("SHA-256 CONTENT HASH:\\s*\\R\\s*([a-fA-F0-9]{64})").matcher(text);
        if (mHash.find()) {
            fields.put("certificateHash", mHash.group(1).trim());
        }

        // 8. Transaction Hash
        Matcher mTx = Pattern.compile("BLOCKCHAIN TRANSACTION HASH:\\s*\\R\\s*(0x[a-fA-F0-9]{64})").matcher(text);
        if (mTx.find()) {
            fields.put("transactionHash", mTx.group(1).trim());
        }

        // 9. Institution
        Matcher mInst = Pattern.compile("^\\s*(.*?)\\s*\\R\\s*OFFICIAL BLOCKCHAIN-BACKED", Pattern.DOTALL).matcher(text);
        if (mInst.find()) {
            String inst = mInst.group(1).replaceAll("\\R", " ").replaceAll("\\s+", " ").trim();
            fields.put("institution", inst);
        }

        return fields;
    }

    private VerifyResponse finalizeIdVerify(VerifyResponse response, String certificateId) {
        if (auditService != null) {
            auditService.logEvent(
                "VERIFY_ATTEMPT",
                "ANONYMOUS",
                null,
                response.getVerificationResult(),
                Map.of(
                    "method", "ID_LOOKUP",
                    "certificateId", certificateId != null ? certificateId : "UNKNOWN",
                    "result", response.getVerificationResult()
                )
            );
        }
        return response;
    }

    private VerifyResponse enrichWithAiAndAudit(
            VerifyResponse resp,
            String pdfText,
            Map<String, String> extracted,
            Certificate cert,
            List<FieldMismatch> mismatches) {

        boolean isTampered = "TAMPERING_DETECTED".equals(resp.getVerificationResult());

        if (aiAnalysisService != null) {
            try {
                AiAnalysisResult aiResult = aiAnalysisService.analyzeDocument(
                        pdfText, extracted, cert, isTampered, mismatches
                );
                resp.setAiAnalysis(aiResult);
            } catch (Exception e) {
                log.warn("AI analysis failed: {}", e.getMessage());
            }
        }

        if (auditService != null) {
            String certId = cert != null ? cert.getCertificateId() : (extracted != null ? extracted.get("certificateId") : "UNKNOWN");
            auditService.logEvent(
                    "VERIFY_ATTEMPT",
                    "ANONYMOUS",
                    null,
                    resp.getVerificationResult(),
                    Map.of(
                        "method", "PDF_UPLOAD",
                        "certificateId", certId != null ? certId : "UNKNOWN",
                        "result", resp.getVerificationResult()
                    )
            );

            if (isTampered) {
                auditService.logEvent(
                        "TAMPER_DETECTED",
                        "ANONYMOUS",
                        null,
                        "WARNING",
                        Map.of(
                            "certificateId", certId != null ? certId : "UNKNOWN",
                            "mismatches", mismatches != null ? mismatches.size() : 0
                        )
                );
            }
        }

        return resp;
    }
}
