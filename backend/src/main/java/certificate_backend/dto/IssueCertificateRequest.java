package certificate_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for {@code POST /api/certificates/issue}.
 *
 * <p>All fields except {@code grade} are required. Validation is enforced by
 * Bean Validation ({@code @Valid} on the controller parameter).
 *
 * <p>Note: {@code transactionHash} is NOT accepted in this request.
 * It will be set by the blockchain integration layer once the Solidity
 * contract is deployed. Accepting it here would allow clients to supply
 * a fake hash.
 */
@Data
public class IssueCertificateRequest {

    /** Full name of the certificate holder. */
    @NotBlank(message = "Student name is required")
    @Size(max = 200, message = "Student name must not exceed 200 characters")
    private String studentName;

    /** Institutional roll number or student ID. */
    @NotBlank(message = "Student roll number is required")
    @Size(max = 100, message = "Roll number must not exceed 100 characters")
    private String studentRollNumber;

    /** Programme or course title. */
    @NotBlank(message = "Course is required")
    @Size(max = 300, message = "Course must not exceed 300 characters")
    private String course;

    /** Full name of the issuing institution. */
    @NotBlank(message = "Institution is required")
    @Size(max = 300, message = "Institution must not exceed 300 characters")
    private String institution;

    /**
     * Grade or classification awarded.
     * Optional — if not provided, the hash will include an empty string for this field.
     */
    @Size(max = 200, message = "Grade must not exceed 200 characters")
    private String grade;

    /**
     * Date of issue in ISO-8601 format (YYYY-MM-DD).
     * Stored as a String to avoid timezone ambiguity.
     * Included in the content hash.
     */
    @NotBlank(message = "Issue date is required")
    @Pattern(
        regexp = "\\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\\d|3[01])",
        message = "Issue date must be in YYYY-MM-DD format"
    )
    private String issueDate;
}
