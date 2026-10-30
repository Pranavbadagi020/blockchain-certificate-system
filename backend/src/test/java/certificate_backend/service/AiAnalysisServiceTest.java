package certificate_backend.service;

import certificate_backend.dto.AiAnalysisResult;
import certificate_backend.dto.FieldMismatch;
import certificate_backend.model.Certificate;
import certificate_backend.model.CertificateStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AiAnalysisService")
class AiAnalysisServiceTest {

    private AiAnalysisService aiService;

    @BeforeEach
    void setUp() {
        aiService = new AiAnalysisServiceImpl(
                true,
                "offline",
                "",
                "https://api.openai.com/v1/chat/completions",
                3000
        );
    }

    @Test
    @DisplayName("should report LOW risk for clean certificate with matching details")
    void shouldReportLowRiskForCleanCertificate() {
        String cleanText = "Indian Institute of Technology Delhi\\nCertificate of Degree\\n" +
                "This is to certify that Arjun Mehta, Roll No: CS2020001, has completed B.Tech Computer Science\\n" +
                "Grade: First Class with Distinction. Issue Date: 2024-05-15.";

        Map<String, String> fields = new HashMap<>();
        fields.put("studentName", "Arjun Mehta");
        fields.put("studentRollNumber", "CS2020001");
        fields.put("course", "B.Tech Computer Science");
        fields.put("institution", "IIT Delhi");
        fields.put("grade", "First Class with Distinction");
        fields.put("issueDate", "2024-05-15");

        Certificate cert = Certificate.builder()
                .certificateId("CERT-2024-10001")
                .studentName("Arjun Mehta")
                .studentRollNumber("CS2020001")
                .course("B.Tech Computer Science")
                .institution("IIT Delhi")
                .grade("First Class with Distinction")
                .issueDate("2024-05-15")
                .status(CertificateStatus.VALID)
                .build();

        AiAnalysisResult result = aiService.analyzeDocument(
                cleanText, fields, cert, false, Collections.emptyList()
        );

        assertThat(result.getAnalysisStatus()).isEqualTo("COMPLETED");
        assertThat(result.getRiskLevel()).isEqualTo("LOW");
        assertThat(result.getDisclaimer()).contains("advisory heuristic");
        assertThat(result.getModelOrMethod()).contains("CertChain Heuristic Anomaly Engine");
    }

    @Test
    @DisplayName("should report HIGH risk when deterministic tampering is detected")
    void shouldReportHighRiskWhenTamperingDetected() {
        String modifiedText = "Indian Institute of Technology Delhi\\nThis certifies Rahulkumar\\nRoll: CS2020001";
        Map<String, String> fields = Map.of(
                "studentName", "Rahulkumar",
                "studentRollNumber", "CS2020001"
        );

        Certificate cert = Certificate.builder()
                .certificateId("CERT-2024-10001")
                .studentName("Shivakumar")
                .studentRollNumber("CS2020001")
                .status(CertificateStatus.VALID)
                .build();

        List<FieldMismatch> mismatches = List.of(
                FieldMismatch.builder()
                        .field("studentName")
                        .expected("Shivakumar")
                        .found("Rahulkumar")
                        .message("Student name mismatch")
                        .build()
        );

        AiAnalysisResult result = aiService.analyzeDocument(
                modifiedText, fields, cert, true, mismatches
        );

        assertThat(result.getRiskLevel()).isEqualTo("HIGH");
        assertThat(result.getSuspiciousIndicators()).anyMatch(i -> i.contains("Discrepancies identified"));
        assertThat(result.getExplanation()).contains("Significant anomalies");
        assertThat(result.getRecommendedAction()).contains("fraud review");
    }

    @Test
    @DisplayName("should detect suspicious watermark keywords like photoshop or dummy")
    void shouldDetectSuspiciousKeywords() {
        String suspectText = "Official Certificate specimen dummy copy edited with photoshop";
        Map<String, String> fields = Map.of(
                "studentName", "John Doe",
                "issueDate", "2024-01-01"
        );

        AiAnalysisResult result = aiService.analyzeDocument(
                suspectText, fields, null, false, Collections.emptyList()
        );

        assertThat(result.getRiskLevel()).isEqualTo("HIGH");
        assertThat(result.getSuspiciousIndicators()).anyMatch(i -> i.contains("photoshop"));
    }

    @Test
    @DisplayName("should report MEDIUM risk on unusually low text density")
    void shouldReportMediumRiskOnLowTextDensity() {
        String sparseText = "Short text";
        Map<String, String> fields = Map.of("studentName", "Test");

        AiAnalysisResult result = aiService.analyzeDocument(
                sparseText, fields, null, false, Collections.emptyList()
        );

        assertThat(result.getRiskLevel()).isEqualTo("MEDIUM");
        assertThat(result.getSuspiciousIndicators()).anyMatch(i -> i.contains("low extractable text density"));
    }

    @Test
    @DisplayName("should return DISABLED status when AI is toggled off")
    void shouldReturnDisabledWhenConfiguredOff() {
        AiAnalysisService disabledService = new AiAnalysisServiceImpl(
                false, "offline", "", "", 1000
        );

        AiAnalysisResult result = disabledService.analyzeDocument(
                "Any text", Collections.emptyMap(), null, false, Collections.emptyList()
        );

        assertThat(result.getAnalysisStatus()).isEqualTo("DISABLED");
        assertThat(result.getRiskLevel()).isEqualTo("UNKNOWN");
    }
}
