package certificate_backend.service;

import certificate_backend.dto.AiAnalysisResult;
import certificate_backend.dto.FieldMismatch;
import certificate_backend.model.Certificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Year;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiAnalysisServiceImpl implements AiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisServiceImpl.class);

    private static final String ADVISORY_DISCLAIMER =
            "AI-assisted document analysis is an advisory heuristic that supplements deterministic cryptographic verification and cannot replace blockchain-verified authenticity.";

    private static final List<String> SUSPICIOUS_KEYWORDS = List.of(
            "photoshop", "gimp", "canva", "watermark removed", "specimen",
            "sample copy", "dummy", "draft", "forgery", "fake", "unofficial",
            "modified", "recreated", "unverified copy"
    );

    private final boolean enabled;
    private final String provider;
    private final String apiKey;
    private final String endpoint;
    private final long timeoutMs;

    public AiAnalysisServiceImpl(
            @Value("${app.ai.enabled:true}") boolean enabled,
            @Value("${app.ai.provider:offline}") String provider,
            @Value("${app.ai.api-key:}") String apiKey,
            @Value("${app.ai.endpoint:https://api.openai.com/v1/chat/completions}") String endpoint,
            @Value("${app.ai.timeout-ms:3000}") long timeoutMs) {
        this.enabled = enabled;
        this.provider = provider != null ? provider.trim().toLowerCase() : "offline";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.endpoint = endpoint != null ? endpoint.trim() : "";
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 3000;
    }

    @Override
    public AiAnalysisResult analyzeDocument(
            String rawExtractedText,
            Map<String, String> extractedFields,
            Certificate authoritativeRecord,
            boolean deterministicTamperDetected,
            List<FieldMismatch> mismatches) {

        if (!enabled) {
            return AiAnalysisResult.builder()
                    .analysisStatus("DISABLED")
                    .riskLevel("UNKNOWN")
                    .suspiciousIndicators(Collections.emptyList())
                    .explanation("AI-assisted analysis is disabled in system configuration.")
                    .recommendedAction("Rely on deterministic cryptographic verification.")
                    .modelOrMethod("Disabled")
                    .disclaimer(ADVISORY_DISCLAIMER)
                    .build();
        }

        // Try external LLM provider if configured with an API key
        if ("openai".equals(provider) && !apiKey.isBlank()) {
            try {
                AiAnalysisResult externalResult = callExternalLlm(
                        rawExtractedText, extractedFields, deterministicTamperDetected, mismatches
                );
                if (externalResult != null) {
                    return externalResult;
                }
            } catch (Exception e) {
                log.warn("External AI provider call failed ({}), falling back to local heuristic analyzer: {}",
                        provider, e.getMessage());
            }
        }

        // Default: CertChain Heuristic Anomaly Engine (Offline / Local)
        return performHeuristicAnalysis(
                rawExtractedText, extractedFields, authoritativeRecord, deterministicTamperDetected, mismatches,
                "CertChain Heuristic Anomaly Engine v1.0 (Local/Offline)"
        );
    }

    private AiAnalysisResult performHeuristicAnalysis(
            String rawText,
            Map<String, String> fields,
            Certificate authoritativeRecord,
            boolean deterministicTamperDetected,
            List<FieldMismatch> mismatches,
            String methodLabel) {

        List<String> indicators = new ArrayList<>();
        String normalizedText = (rawText != null ? rawText.toLowerCase() : "");

        // 1. Deterministic Tampering Indicators
        if (deterministicTamperDetected || (mismatches != null && !mismatches.isEmpty())) {
            indicators.add("Discrepancies identified between document fields and authoritative registry record.");
            if (mismatches != null) {
                for (FieldMismatch m : mismatches) {
                    indicators.add("Mismatch on field [" + m.getField() + "]: expected '"
                            + m.getExpected() + "', found '" + m.getFound() + "'.");
                }
            }
        }

        // 2. Suspicious keywords or watermark indicators
        for (String kw : SUSPICIOUS_KEYWORDS) {
            if (normalizedText.contains(kw)) {
                indicators.add("Document contains suspicious keyword/watermark artifact: '" + kw + "'.");
            }
        }

        // 3. Structural & Text Density Analysis
        if (rawText == null || rawText.trim().length() < 60) {
            indicators.add("Unusually low extractable text density (under 60 characters). Document may be an un-OCR raster scan or non-standard format.");
        }

        // 4. Academic Credential Consistency
        if (fields != null) {
            // Check for missing core identifiers
            if (!fields.containsKey("studentRollNumber") && !fields.containsKey("rollNumber")) {
                indicators.add("Academic roll/registration number could not be extracted from document structure.");
            }
            if (!fields.containsKey("issueDate") && !fields.containsKey("date")) {
                indicators.add("Standard issuance date stamp missing from readable document metadata.");
            }

            // Check date realism
            String dateVal = fields.getOrDefault("issueDate", fields.get("date"));
            if (dateVal != null) {
                Matcher yearMatcher = Pattern.compile("\\b(19\\d\\d|20\\d\\d)\\b").matcher(dateVal);
                if (yearMatcher.find()) {
                    int year = Integer.parseInt(yearMatcher.group(1));
                    int currentYear = Year.now().getValue();
                    if (year > currentYear + 1) {
                        indicators.add("Certificate issuance year (" + year + ") is in the future.");
                    } else if (year < 1960) {
                        indicators.add("Certificate issuance year (" + year + ") predates modern digital accreditation.");
                    }
                }
            }

            // Contradictory academic rating checks
            String grade = fields.getOrDefault("grade", "").toLowerCase();
            if (grade.contains("distinction") || grade.contains("first class") || grade.contains("honors")) {
                if (normalizedText.contains("remedial") || normalizedText.contains("withheld")
                        || normalizedText.contains("repeat") || normalizedText.contains("fail")) {
                    indicators.add("Contradictory academic descriptors: positive distinction co-occurs with disciplinary/fail terms.");
                }
            }
        }

        // 5. Evaluate Risk Level
        String riskLevel;
        String explanation;
        String recommendedAction;

        if (deterministicTamperDetected || indicators.stream().anyMatch(i ->
                i.contains("Discrepancies") || i.contains("suspicious keyword") || i.contains("Contradictory"))) {
            riskLevel = "HIGH";
            explanation = "Significant anomalies identified. The document exhibits explicit cryptographic mismatches, suspicious metadata tags, or contradictory academic claims.";
            recommendedAction = "Do not accept document as authentic. Escalate to academic registrar or security compliance team for fraud review.";
        } else if (indicators.size() >= 2 || indicators.stream().anyMatch(i -> i.contains("Unusually low") || i.contains("future"))) {
            riskLevel = "MEDIUM";
            explanation = "Moderate structural or formatting anomalies observed. Core fields or document density deviate from typical institutional templates.";
            recommendedAction = "Perform secondary review against official university records or request an original certified re-issuance.";
        } else if (indicators.size() == 1) {
            riskLevel = "LOW";
            explanation = "Minor structural note detected. Document contents remain consistent with registry expectations.";
            recommendedAction = "Verify on-chain status and inspect standard physical seal.";
        } else {
            riskLevel = "LOW";
            explanation = "Textual phrasing, structure, and field definitions conform to expected academic certificate conventions. No suspicious anomalies detected.";
            recommendedAction = "Proceed with standard verification workflow. Document appears structurally authentic.";
        }

        return AiAnalysisResult.builder()
                .analysisStatus("COMPLETED")
                .riskLevel(riskLevel)
                .suspiciousIndicators(indicators)
                .explanation(explanation)
                .recommendedAction(recommendedAction)
                .modelOrMethod(methodLabel)
                .disclaimer(ADVISORY_DISCLAIMER)
                .build();
    }

    private AiAnalysisResult callExternalLlm(
            String rawText,
            Map<String, String> fields,
            boolean deterministicTamperDetected,
            List<FieldMismatch> mismatches) {

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .build();

            String sanitizedText = (rawText != null) ? rawText.replace('"', ' ').replace('\n', ' ') : "";
            if (sanitizedText.length() > 300) {
                sanitizedText = sanitizedText.substring(0, 300);
            }

            String prompt = "Evaluate certificate authenticity. Tampered: " + deterministicTamperDetected
                    + ". Snippet: " + sanitizedText;

            String requestBody = "{\"model\": \"gpt-4o-mini\", \"messages\": [{\"role\": \"user\", \"content\": \""
                    + prompt + "\"}]}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                String riskLevel = "LOW";
                if (body != null && (body.contains("HIGH") || body.contains("high"))) {
                    riskLevel = "HIGH";
                } else if (body != null && (body.contains("MEDIUM") || body.contains("medium"))) {
                    riskLevel = "MEDIUM";
                }

                return AiAnalysisResult.builder()
                        .analysisStatus("COMPLETED")
                        .riskLevel(riskLevel)
                        .suspiciousIndicators(List.of("External AI provider evaluated document text structure."))
                        .explanation("External LLM evaluated document consistency and linguistic structure.")
                        .recommendedAction(riskLevel.equals("HIGH") ? "Escalate for manual inspection." : "Proceed with cryptographic verification.")
                        .modelOrMethod("OpenAI (" + provider + ") via Secure Backend Proxy")
                        .disclaimer(ADVISORY_DISCLAIMER)
                        .build();
            } else {
                log.warn("External AI returned non-200 code: {}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("External AI request threw exception: {}", e.getMessage());
        }

        return null;
    }
}
