package certificate_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a field-level comparison between what the PDF displays
 * and what the QR credential asserts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InfosysFieldComparison {

    /** The value extracted from the visible PDF text. Null if not extractable. */
    private String pdfValue;

    /** The value from the parsed QR credential. Null if not present. */
    private String credentialValue;

    /**
     * Whether the two values match after normalisation.
     * Null if either side could not be extracted.
     */
    private Boolean matches;

    /**
     * Confidence level for the PDF extraction (HIGH / MEDIUM / LOW).
     * LOW indicates manual review is recommended.
     */
    private String pdfExtractionConfidence;

    /** Human-readable note about the comparison outcome. */
    private String note;
}

