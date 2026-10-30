package certificate_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisResult {

    /** Status of analysis: COMPLETED, UNAVAILABLE, or DISABLED */
    private String analysisStatus;

    /** Overall assessment: LOW, MEDIUM, HIGH, or UNKNOWN */
    private String riskLevel;

    /** List of identified suspicious document characteristics */
    private List<String> suspiciousIndicators;

    /** Explanatory narrative explaining the risk assessment */
    private String explanation;

    /** Prescriptive guidance for verifying party */
    private String recommendedAction;

    /** Model or method employed (e.g. Heuristic v1.0, OpenAI gpt-4o-mini) */
    private String modelOrMethod;

    /** Advisory disclaimer distinguishing heuristic analysis from cryptographic proof */
    private String disclaimer;
}
