package certificate_backend.service;

import certificate_backend.dto.CertificateResponse;
import certificate_backend.dto.IssueCertificateRequest;
import certificate_backend.dto.VerifyResponse;
import certificate_backend.exception.CertificateNotFoundException;
import certificate_backend.exception.DuplicateCertificateException;
import certificate_backend.model.Certificate;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import java.io.ByteArrayOutputStream;

import certificate_backend.model.CertificateStatus;
import certificate_backend.repository.CertificateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CertificateService}.
 *
 * <p>No Spring context or MongoDB is needed — all repository calls are mocked.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CertificateService")
class CertificateServiceTest {

    @Mock
    private CertificateRepository repository;

    @Mock
    private BlockchainService blockchainService;

    @InjectMocks
    private CertificateService service;

    // ── Hashing ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("computeHash()")
    class ComputeHashTests {

        @Test
        @DisplayName("produces a 64-character hex string")
        void hashIs64CharHex() {
            String hash = service.computeHash(
                "Arjun Mehta", "CS2020001",
                "B.Tech CS", "IIT Delhi",
                "First Class", "2024-05-15"
            );
            assertThat(hash)
                .hasSize(64)
                .matches("[0-9a-f]+");
        }

        @Test
        @DisplayName("is deterministic — same inputs always produce same hash")
        void hashIsDeterministic() {
            String h1 = service.computeHash("Alice", "R001", "CS", "MIT", "A", "2024-01-01");
            String h2 = service.computeHash("Alice", "R001", "CS", "MIT", "A", "2024-01-01");
            assertThat(h1).isEqualTo(h2);
        }

        @Test
        @DisplayName("changes when any field changes — studentName")
        void hashChangesOnNameChange() {
            String h1 = service.computeHash("Alice", "R001", "CS", "MIT", "A", "2024-01-01");
            String h2 = service.computeHash("Alice Smith", "R001", "CS", "MIT", "A", "2024-01-01");
            assertThat(h1).isNotEqualTo(h2);
        }

        @Test
        @DisplayName("changes when any field changes — issueDate")
        void hashChangesOnDateChange() {
            String h1 = service.computeHash("Alice", "R001", "CS", "MIT", "A", "2024-01-01");
            String h2 = service.computeHash("Alice", "R001", "CS", "MIT", "A", "2024-01-02");
            assertThat(h1).isNotEqualTo(h2);
        }

        @Test
        @DisplayName("uses pipe separator — fields cannot bleed into each other")
        void separatorPreventsFieldBleeding() {
            // "AB|CD" must differ from "A|BCD"
            String h1 = service.computeHash("AB", "R", "CD", "X", "", "2024-01-01");
            String h2 = service.computeHash("A",  "R", "BCD", "X", "", "2024-01-01");
            assertThat(h1).isNotEqualTo(h2);
        }

        @Test
        @DisplayName("empty grade produces same hash as explicit empty string")
        void emptyGradeIsConsistent() {
            String h1 = service.computeHash("Alice", "R001", "CS", "MIT", "", "2024-01-01");
            String h2 = service.computeHash("Alice", "R001", "CS", "MIT", "", "2024-01-01");
            assertThat(h1).isEqualTo(h2);
        }

        @Test
        @DisplayName("known fixed hash — regression guard")
        void knownFixedHash() {
            // Actual SHA-256 of "Alice|R001|CS|MIT|A|2024-01-01" (verified by running JVM)
            String expected = "4e51a2fcee4ae407dcc9b9d7796155321c2ffa05c91fa597c8930dc325d9b568";
            String actual = service.computeHash("Alice", "R001", "CS", "MIT", "A", "2024-01-01");
            assertThat(actual).isEqualTo(expected);
        }
    }

    // ── Issue Certificate ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("issueCertificate()")
    class IssueTests {

        private IssueCertificateRequest request;

        @BeforeEach
        void setUp() throws Exception {
            request = new IssueCertificateRequest();
            request.setStudentName("Arjun Mehta");
            request.setStudentRollNumber("CS2020001");
            request.setCourse("B.Tech Computer Science");
            request.setInstitution("IIT Delhi");
            request.setGrade("First Class with Distinction");
            request.setIssueDate("2024-05-15");

            lenient().when(blockchainService.issueCertificate(anyString(), anyString()))
                    .thenReturn("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
        }

        @Test
        @DisplayName("saves and returns certificate with VALID status and transaction hash")
        void issuesCertificateWithValidStatus() throws Exception {
            when(repository.existsByCertificateId(anyString())).thenReturn(false);
            when(repository.save(any(Certificate.class))).thenAnswer(inv -> {
                Certificate c = inv.getArgument(0);
                c.setId("mongo-object-id");
                c.setCreatedAt(Instant.now());
                return c;
            });

            CertificateResponse response = service.issueCertificate(request);

            assertThat(response.getStatus()).isEqualTo(CertificateStatus.VALID);
            assertThat(response.getStudentName()).isEqualTo("Arjun Mehta");
            assertThat(response.getCertificateId()).matches("CERT-\\d{4}-\\d{5}");
            assertThat(response.getCertificateHash()).hasSize(64);
            assertThat(response.getTransactionHash())
                    .isEqualTo("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
            verify(blockchainService, times(1)).issueCertificate(
                    eq(response.getCertificateId()),
                    eq(response.getCertificateHash())
            );
            verify(repository, times(1)).save(any(Certificate.class));
        }

        @Test
        @DisplayName("transaction hash is recorded from blockchain issuance")
        void transactionHashIsRecordedFromBlockchain() throws Exception {
            when(repository.existsByCertificateId(anyString())).thenReturn(false);
            when(blockchainService.issueCertificate(anyString(), anyString()))
                    .thenReturn("0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890");
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CertificateResponse response = service.issueCertificate(request);
            assertThat(response.getTransactionHash())
                    .isEqualTo("0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890");
        }

        @Test
        @DisplayName("throws exception and does not call repository.save() when blockchain issuance fails")
        void throwsAndDoesNotSaveWhenBlockchainFails() throws Exception {
            when(repository.existsByCertificateId(anyString())).thenReturn(false);
            when(blockchainService.issueCertificate(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Blockchain RPC timeout"));

            assertThatThrownBy(() -> service.issueCertificate(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Blockchain certificate issuance failed");

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("attempts compensating on-chain revocation when MongoDB save fails after blockchain issuance")
        void attemptsCompensatingRevocationWhenMongoSaveFails() throws Exception {
            when(repository.existsByCertificateId(anyString())).thenReturn(false);
            when(blockchainService.issueCertificate(anyString(), anyString()))
                    .thenReturn("0xtxhash123");
            when(repository.save(any()))
                    .thenThrow(new RuntimeException("MongoDB connection lost"));

            assertThatThrownBy(() -> service.issueCertificate(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to save certificate to database after blockchain issuance");

            verify(blockchainService, times(1)).revokeCertificate(anyString());
        }

        @Test
        @DisplayName("throws error even if compensating on-chain revocation also fails")
        void errorThrownEvenIfCompensatingRevocationFails() throws Exception {
            when(repository.existsByCertificateId(anyString())).thenReturn(false);
            when(blockchainService.issueCertificate(anyString(), anyString()))
                    .thenReturn("0xtxhash123");
            when(repository.save(any()))
                    .thenThrow(new RuntimeException("MongoDB connection lost"));
            when(blockchainService.revokeCertificate(anyString()))
                    .thenThrow(new RuntimeException("Revocation transaction reverted"));

            assertThatThrownBy(() -> service.issueCertificate(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to save certificate to database after blockchain issuance");

            verify(blockchainService, times(1)).revokeCertificate(anyString());
        }

        @Test
        @DisplayName("throws DuplicateCertificateException when ID already exists")
        void throwsOnDuplicateId() {
            when(repository.existsByCertificateId(anyString())).thenReturn(true);

            assertThatThrownBy(() -> service.issueCertificate(request))
                .isInstanceOf(DuplicateCertificateException.class)
                .hasMessageContaining("already exists");

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("trims whitespace from all string fields before hashing")
        void trimsWhitespaceBeforeHashing() {
            request.setStudentName("  Arjun Mehta  ");
            request.setStudentRollNumber("  CS2020001  ");

            when(repository.existsByCertificateId(anyString())).thenReturn(false);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CertificateResponse r = service.issueCertificate(request);
            assertThat(r.getStudentName()).isEqualTo("Arjun Mehta");
            assertThat(r.getStudentRollNumber()).isEqualTo("CS2020001");
        }

        @Test
        @DisplayName("null grade is stored as empty string in hash")
        void nullGradeStoredAsEmpty() {
            request.setGrade(null);
            when(repository.existsByCertificateId(anyString())).thenReturn(false);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CertificateResponse r = service.issueCertificate(request);
            assertThat(r.getGrade()).isEqualTo("");
            assertThat(r.getCertificateHash()).hasSize(64);
        }
    }

    // ── Verify Certificate ────────────────────────────────────────────────────

    @Nested
    @DisplayName("verifyCertificate()")
    class VerifyTests {

        private Certificate validCert;

        @BeforeEach
        void setUp() throws Exception {
            String hash = service.computeHash(
                "Alice", "R001", "CS", "MIT", "A", "2024-01-01"
            );
            validCert = Certificate.builder()
                .id("mongo-id")
                .certificateId("CERT-2024-10001")
                .studentName("Alice")
                .studentRollNumber("R001")
                .course("CS")
                .institution("MIT")
                .grade("A")
                .issueDate("2024-01-01")
                .certificateHash(hash)
                .status(CertificateStatus.VALID)
                .createdAt(Instant.now())
                .build();

            lenient().when(blockchainService.getCertificate(anyString()))
                .thenReturn(new BlockchainService.CertificateChainDetails(
                    hash,
                    "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266",
                    BigInteger.valueOf(1700000000L),
                    false
                ));
        }

        @Test
        @DisplayName("returns VALID when certificate matches in MongoDB and on-chain")
        void returnsValid() {
            when(repository.findByCertificateId("CERT-2024-10001"))
                .thenReturn(Optional.of(validCert));

            VerifyResponse resp = service.verifyCertificate("CERT-2024-10001");

            assertThat(resp.getVerificationResult()).isEqualTo("VALID");
            assertThat(resp.isBlockchainVerified()).isTrue();
            assertThat(resp.getCertificate().getCertificateId()).isEqualTo("CERT-2024-10001");
            assertThat(resp.getBlockchainNote()).contains("verified");
        }

        @Test
        @DisplayName("returns TAMPERED when database data does not match stored hash")
        void returnsTamperedWhenDatabaseHashMismatch() {
            validCert.setStudentName("Alice Tampered"); // name changed but hash not updated
            when(repository.findByCertificateId("CERT-2024-10001"))
                .thenReturn(Optional.of(validCert));

            VerifyResponse resp = service.verifyCertificate("CERT-2024-10001");
            assertThat(resp.getVerificationResult()).isEqualTo("TAMPERED");
            assertThat(resp.isBlockchainVerified()).isFalse();
        }

        @Test
        @DisplayName("missing on-chain record does not return VALID")
        void missingOnChainRecordDoesNotReturnValid() throws Exception {
            when(repository.findByCertificateId("CERT-2024-10001"))
                .thenReturn(Optional.of(validCert));
            when(blockchainService.getCertificate("CERT-2024-10001"))
                .thenReturn(new BlockchainService.CertificateChainDetails(
                    "0000000000000000000000000000000000000000000000000000000000000000",
                    "0x0000000000000000000000000000000000000000",
                    BigInteger.ZERO,
                    false
                ));

            VerifyResponse resp = service.verifyCertificate("CERT-2024-10001");
            assertThat(resp.getVerificationResult()).isNotEqualTo("VALID");
            assertThat(resp.isBlockchainVerified()).isFalse();
            assertThat(resp.getVerificationResult()).isEqualTo("NOT_FOUND");
        }

        @Test
        @DisplayName("returns TAMPERED when blockchain hash does not match computed hash")
        void returnsTamperedWhenBlockchainHashMismatch() throws Exception {
            when(repository.findByCertificateId("CERT-2024-10001"))
                .thenReturn(Optional.of(validCert));
            when(blockchainService.getCertificate("CERT-2024-10001"))
                .thenReturn(new BlockchainService.CertificateChainDetails(
                    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266",
                    BigInteger.valueOf(1700000000L),
                    false
                ));

            VerifyResponse resp = service.verifyCertificate("CERT-2024-10001");
            assertThat(resp.getVerificationResult()).isEqualTo("TAMPERED");
            assertThat(resp.isBlockchainVerified()).isFalse();
        }

        @Test
        @DisplayName("returns REVOKED when certificate is revoked on-chain")
        void returnsRevokedWhenOnChainRevoked() throws Exception {
            when(repository.findByCertificateId("CERT-2024-10001"))
                .thenReturn(Optional.of(validCert));
            when(blockchainService.getCertificate("CERT-2024-10001"))
                .thenReturn(new BlockchainService.CertificateChainDetails(
                    validCert.getCertificateHash(),
                    "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266",
                    BigInteger.valueOf(1700000000L),
                    true
                ));

            VerifyResponse resp = service.verifyCertificate("CERT-2024-10001");
            assertThat(resp.getVerificationResult()).isEqualTo("REVOKED");
            assertThat(resp.isBlockchainVerified()).isTrue();
        }

        @Test
        @DisplayName("database-revoked certificate cannot return VALID")
        void returnsRevokedWhenDatabaseRevoked() {
            validCert.setStatus(CertificateStatus.REVOKED);
            when(repository.findByCertificateId("CERT-2024-10001"))
                .thenReturn(Optional.of(validCert));

            VerifyResponse resp = service.verifyCertificate("CERT-2024-10001");
            assertThat(resp.getVerificationResult()).isNotEqualTo("VALID");
            assertThat(resp.getVerificationResult()).isEqualTo("REVOKED");
            assertThat(resp.isBlockchainVerified()).isTrue();
        }

        @Test
        @DisplayName("blockchain RPC failure does not return VALID and is handled safely")
        void blockchainRpcFailureDoesNotReturnValid() throws Exception {
            when(repository.findByCertificateId("CERT-2024-10001"))
                .thenReturn(Optional.of(validCert));
            when(blockchainService.getCertificate("CERT-2024-10001"))
                .thenThrow(new java.io.IOException("RPC node connection refused"));

            VerifyResponse resp = service.verifyCertificate("CERT-2024-10001");
            assertThat(resp.getVerificationResult()).isNotEqualTo("VALID");
            assertThat(resp.getVerificationResult()).isEqualTo("BLOCKCHAIN_UNAVAILABLE");
            assertThat(resp.isBlockchainVerified()).isFalse();
            assertThat(resp.getBlockchainNote()).contains("Unable to reach the blockchain");
        }

        @Test
        @DisplayName("throws CertificateNotFoundException for unknown ID in database")
        void throwsForUnknownId() {
            when(repository.findByCertificateId("CERT-9999-99999"))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.verifyCertificate("CERT-9999-99999"))
                .isInstanceOf(CertificateNotFoundException.class)
                .hasMessageContaining("CERT-9999-99999");
        }
    }

    // ── Revoke Certificate ────────────────────────────────────────────────────

    @Nested
    @DisplayName("revokeCertificate()")
    class RevokeTests {

        private Certificate validCert;

        @BeforeEach
        void setUp() {
            String hash = service.computeHash("Bob", "R002", "MBA", "Harvard", "B", "2023-06-01");
            validCert = Certificate.builder()
                .id("mongo-id-2")
                .certificateId("CERT-2023-20001")
                .studentName("Bob")
                .studentRollNumber("R002")
                .course("MBA")
                .institution("Harvard")
                .grade("B")
                .issueDate("2023-06-01")
                .certificateHash(hash)
                .status(CertificateStatus.VALID)
                .createdAt(Instant.now())
                .build();
        }

        @Test
        @DisplayName("successful on-chain revocation updates MongoDB status to REVOKED and returns certificate")
        void successfulRevocationUpdatesMongoAndReturnsRevoked() throws Exception {
            when(repository.findByCertificateId("CERT-2023-20001"))
                .thenReturn(Optional.of(validCert));
            when(blockchainService.revokeCertificate("CERT-2023-20001"))
                .thenReturn("0xrevoketx123");
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CertificateResponse response = service.revokeCertificate("CERT-2023-20001");

            assertThat(response.getStatus()).isEqualTo(CertificateStatus.REVOKED);

            InOrder inOrder = inOrder(blockchainService, repository);
            inOrder.verify(blockchainService).revokeCertificate("CERT-2023-20001");
            inOrder.verify(repository).save(argThat(c -> c.getStatus() == CertificateStatus.REVOKED));
        }

        @Test
        @DisplayName("throws CertificateNotFoundException when certificate does not exist in MongoDB")
        void throwsForMissingCertificate() throws Exception {
            when(repository.findByCertificateId("CERT-0000-00000"))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.revokeCertificate("CERT-0000-00000"))
                .isInstanceOf(CertificateNotFoundException.class);

            verify(blockchainService, never()).revokeCertificate(anyString());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("already-revoked certificate does not trigger an unnecessary blockchain transaction")
        void alreadyRevokedDoesNotTriggerBlockchainTransaction() throws Exception {
            validCert.setStatus(CertificateStatus.REVOKED);
            when(repository.findByCertificateId("CERT-2023-20001"))
                .thenReturn(Optional.of(validCert));

            CertificateResponse response = service.revokeCertificate("CERT-2023-20001");

            assertThat(response.getStatus()).isEqualTo(CertificateStatus.REVOKED);
            verify(blockchainService, never()).revokeCertificate(anyString());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("blockchain revocation failure does not mark MongoDB certificate as revoked")
        void blockchainRevocationFailureDoesNotMarkMongoAsRevoked() throws Exception {
            when(repository.findByCertificateId("CERT-2023-20001"))
                .thenReturn(Optional.of(validCert));
            when(blockchainService.revokeCertificate("CERT-2023-20001"))
                .thenThrow(new RuntimeException("Gas estimation failed: transaction reverted"));

            assertThatThrownBy(() -> service.revokeCertificate("CERT-2023-20001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Blockchain certificate revocation failed");

            verify(repository, never()).save(any());
            assertThat(validCert.getStatus()).isEqualTo(CertificateStatus.VALID);
        }

        @Test
        @DisplayName("MongoDB persistence failure after on-chain revocation is handled and logged")
        void mongoSaveFailureAfterBlockchainRevocationThrowsAndLogs() throws Exception {
            when(repository.findByCertificateId("CERT-2023-20001"))
                .thenReturn(Optional.of(validCert));
            when(blockchainService.revokeCertificate("CERT-2023-20001"))
                .thenReturn("0xrevoketx123");
            when(repository.save(any()))
                .thenThrow(new RuntimeException("MongoDB connection dropped during save"));

            assertThatThrownBy(() -> service.revokeCertificate("CERT-2023-20001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Certificate revoked on blockchain (txHash=0xrevoketx123)");

            verify(blockchainService, times(1)).revokeCertificate("CERT-2023-20001");
            verify(repository, times(1)).save(any());
        }
    }

    // ── PDF Verification & Tamper Detection ─────────────────────────────────

    @Nested
    @DisplayName("verifyCertificatePdf()")
    class VerifyCertificatePdfTests {

        private Certificate validCert;
        private String validHash;

        @BeforeEach
        void setUp() {
            validHash = service.computeHash(
                    "Shivakumar",
                    "CS2024001",
                    "B.Tech Computer Science",
                    "IIT Delhi",
                    "First Class with Distinction",
                    "2024-05-15"
            );

            validCert = Certificate.builder()
                    .certificateId("CERT-2024-10001")
                    .studentName("Shivakumar")
                    .studentRollNumber("CS2024001")
                    .course("B.Tech Computer Science")
                    .institution("IIT Delhi")
                    .grade("First Class with Distinction")
                    .issueDate("2024-05-15")
                    .certificateHash(validHash)
                    .transactionHash("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
                    .status(CertificateStatus.VALID)
                    .createdAt(Instant.now())
                    .build();
        }

        private byte[] buildPdf(
                String inst, String certId, String name, String roll,
                String course, String grade, String date, String hash, String txHash) throws Exception {

            try (PDDocument doc = new PDDocument()) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.setLeading(14.5f);
                    cs.newLineAtOffset(50, 700);

                    cs.showText(inst.toUpperCase());
                    cs.newLine();
                    cs.showText("OFFICIAL BLOCKCHAIN-BACKED CERTIFICATE OF ACHIEVEMENT");
                    cs.newLine();
                    cs.showText("This is to certify that");
                    cs.newLine();
                    cs.showText(name);
                    cs.newLine();
                    cs.showText("Institutional ID / Roll Number: " + roll);
                    cs.newLine();
                    cs.showText("has successfully met all academic requirements and completed the prescribed programme in");
                    cs.newLine();
                    cs.showText(course);
                    cs.newLine();
                    if (grade != null && !grade.isEmpty()) {
                        cs.showText("Grade / Classification: " + grade);
                        cs.newLine();
                    }
                    cs.showText("Conferred on: " + date);
                    cs.newLine();
                    cs.showText("OFFICIAL CERTIFICATE ID");
                    cs.newLine();
                    cs.showText(certId);
                    cs.newLine();
                    cs.showText("SHA-256 CONTENT HASH:");
                    cs.newLine();
                    cs.showText(hash);
                    cs.newLine();
                    cs.showText("BLOCKCHAIN TRANSACTION HASH:");
                    cs.newLine();
                    cs.showText(txHash);
                    cs.newLine();
                    cs.endText();
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                doc.save(baos);
                return baos.toByteArray();
            }
        }

        @Test
        @DisplayName("verifies authentic PDF matching database and blockchain registry")
        void authenticPdfVerifiesSuccessfully() throws Exception {
            byte[] pdf = buildPdf(
                    "IIT Delhi", "CERT-2024-10001", "Shivakumar", "CS2024001",
                    "B.Tech Computer Science", "First Class with Distinction",
                    "2024-05-15", validHash, "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
            );

            when(repository.findByCertificateId("CERT-2024-10001")).thenReturn(Optional.of(validCert));
            when(blockchainService.getCertificate("CERT-2024-10001")).thenReturn(
                    new BlockchainService.CertificateChainDetails(
                            validHash,
                            "0x0000000000000000000000000000000000000001",
                            BigInteger.valueOf(1700000000L),
                            false
                    )
            );

            VerifyResponse response = service.verifyCertificatePdf(pdf);

            assertThat(response.getVerificationResult()).isEqualTo("VERIFIED");
            assertThat(response.isBlockchainVerified()).isTrue();
            assertThat(response.getMismatchDetails()).isNullOrEmpty();
            assertThat(response.getCertificate()).isNotNull();
            assertThat(response.getCertificate().getStudentName()).isEqualTo("Shivakumar");
        }

        @Test
        @DisplayName("detects tampered student name (Shivakumar -> Rahulkumar)")
        void tamperedStudentNameDetected() throws Exception {
            byte[] tamperedPdf = buildPdf(
                    "IIT Delhi", "CERT-2024-10001", "Rahulkumar", "CS2024001",
                    "B.Tech Computer Science", "First Class with Distinction",
                    "2024-05-15", validHash, "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
            );

            when(repository.findByCertificateId("CERT-2024-10001")).thenReturn(Optional.of(validCert));
            when(blockchainService.getCertificate("CERT-2024-10001")).thenReturn(
                    new BlockchainService.CertificateChainDetails(
                            validHash,
                            "0x0000000000000000000000000000000000000001",
                            BigInteger.valueOf(1700000000L),
                            false
                    )
            );

            VerifyResponse response = service.verifyCertificatePdf(tamperedPdf);

            assertThat(response.getVerificationResult()).isEqualTo("TAMPERING_DETECTED");
            assertThat(response.isBlockchainVerified()).isFalse();
            assertThat(response.getMismatchDetails()).isNotEmpty();

            boolean foundNameMismatch = response.getMismatchDetails().stream()
                    .anyMatch(m -> "studentName".equals(m.getField())
                            && m.getExpected().equals("Shivakumar")
                            && m.getFound().equals("Rahulkumar"));
            assertThat(foundNameMismatch).isTrue();
        }

        @Test
        @DisplayName("returns NOT_FOUND when certificate ID in PDF does not exist in registry")
        void nonExistentCertificateIdReturnsNotFound() throws Exception {
            byte[] pdf = buildPdf(
                    "IIT Delhi", "CERT-2024-99999", "Unknown Student", "CS99999",
                    "B.Tech CS", "First Class", "2024-05-15",
                    "0000000000000000000000000000000000000000000000000000000000000000",
                    "0x0000000000000000000000000000000000000000000000000000000000000000"
            );

            when(repository.findByCertificateId("CERT-2024-99999")).thenReturn(Optional.empty());

            VerifyResponse response = service.verifyCertificatePdf(pdf);

            assertThat(response.getVerificationResult()).isEqualTo("NOT_FOUND");
            assertThat(response.isBlockchainVerified()).isFalse();
        }

        @Test
        @DisplayName("returns REVOKED when certificate is revoked in MongoDB")
        void revokedCertificateInDbReturnsRevoked() throws Exception {
            validCert.setStatus(CertificateStatus.REVOKED);
            byte[] pdf = buildPdf(
                    "IIT Delhi", "CERT-2024-10001", "Shivakumar", "CS2024001",
                    "B.Tech Computer Science", "First Class with Distinction",
                    "2024-05-15", validHash, "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
            );

            when(repository.findByCertificateId("CERT-2024-10001")).thenReturn(Optional.of(validCert));

            VerifyResponse response = service.verifyCertificatePdf(pdf);

            assertThat(response.getVerificationResult()).isEqualTo("REVOKED");
            assertThat(response.getMessage()).contains("revoked");
        }

        @Test
        @DisplayName("returns UNABLE_TO_VERIFY when blockchain node is unreachable")
        void blockchainUnreachableReturnsUnableToVerify() throws Exception {
            byte[] pdf = buildPdf(
                    "IIT Delhi", "CERT-2024-10001", "Shivakumar", "CS2024001",
                    "B.Tech Computer Science", "First Class with Distinction",
                    "2024-05-15", validHash, "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
            );

            when(repository.findByCertificateId("CERT-2024-10001")).thenReturn(Optional.of(validCert));
            when(blockchainService.getCertificate("CERT-2024-10001")).thenThrow(new RuntimeException("Connection refused"));

            VerifyResponse response = service.verifyCertificatePdf(pdf);

            assertThat(response.getVerificationResult()).isEqualTo("UNABLE_TO_VERIFY");
            assertThat(response.isBlockchainVerified()).isFalse();
            assertThat(response.getMessage()).contains("unreachable");
        }

        @Test
        @DisplayName("returns UNABLE_TO_VERIFY when PDF is corrupted or missing magic header")
        void corruptedBytesReturnsUnableToVerify() {
            byte[] corrupted = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};

            VerifyResponse response = service.verifyCertificatePdf(corrupted);

            assertThat(response.getVerificationResult()).isEqualTo("UNABLE_TO_VERIFY");
            assertThat(response.getMessage()).contains("valid PDF");
        }

        @Test
        @DisplayName("returns UNABLE_TO_VERIFY when PDF has no Certificate ID")
        void pdfMissingIdReturnsUnableToVerify() throws Exception {
            byte[] pdfWithoutId;
            try (PDDocument doc = new PDDocument()) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText("This is an arbitrary document without any certificate metadata or ID.");
                    cs.endText();
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                doc.save(baos);
                pdfWithoutId = baos.toByteArray();
            }

            VerifyResponse response = service.verifyCertificatePdf(pdfWithoutId);

            assertThat(response.getVerificationResult()).isEqualTo("UNABLE_TO_VERIFY");
            assertThat(response.getMessage()).contains("Certificate ID");
        }

        @Test
        @DisplayName("returns NEEDS_REVIEW when external third-party certificate (e.g. Infosys Springboard) is uploaded")
        void externalCertificateReturnsNeedsReview() throws Exception {
            byte[] pdfWithoutId;
            try (PDDocument doc = new PDDocument()) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText("Infosys Springboard Certificate of Completion awarded to Pranav Kumar");
                    cs.endText();
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                doc.save(baos);
                pdfWithoutId = baos.toByteArray();
            }

            VerifyResponse response = service.verifyCertificatePdf(pdfWithoutId);

            assertThat(response.getVerificationResult()).isEqualTo("NEEDS_REVIEW");
            assertThat(response.isBlockchainVerified()).isFalse();
            assertThat(response.getMessage()).contains("External certificate detected");
            assertThat(response.getMessage()).contains("Infosys Springboard");
        }
    }

}
