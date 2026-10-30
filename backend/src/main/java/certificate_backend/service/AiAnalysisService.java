package certificate_backend.service;

import certificate_backend.dto.AiAnalysisResult;
import certificate_backend.dto.FieldMismatch;
import certificate_backend.model.Certificate;

import java.util.List;
import java.util.Map;

public interface AiAnalysisService {

    /**
     * Analyze extracted document text and metadata for structural anomalies,
     * inconsistent wording, or indicators of tampering.
     *
     * <p>This analysis is strictly advisory and NEVER overrides deterministic
     * cryptographic or blockchain verification results.
     */
    AiAnalysisResult analyzeDocument(
            String rawExtractedText,
            Map<String, String> extractedFields,
            Certificate authoritativeRecord,
            boolean deterministicTamperDetected,
            List<FieldMismatch> mismatches
    );
}
