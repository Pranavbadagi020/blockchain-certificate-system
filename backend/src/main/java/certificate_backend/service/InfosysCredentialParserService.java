package certificate_backend.service;

import certificate_backend.dto.InfosysParsedCredential;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses raw QR text content into a structured {@link InfosysParsedCredential}.
 *
 * <p>Supported QR payload formats:
 * <ol>
 *   <li><b>URL</b> – {@code https://verify.onwingspan.com/?certId=…}
 *       The URL is recorded and returned as the official verification link.
 *       Field-level comparison is not possible from a URL alone.</li>
 *   <li><b>JSON-LD Verifiable Credential</b> – Full W3C VC JSON object.
 *       All available fields are extracted.</li>
 *   <li><b>JWT</b> – Not yet decoded (returns NEEDS_REVIEW).</li>
 *   <li><b>Unknown</b> – Returns NEEDS_REVIEW.</li>
 * </ol>
 *
 * <p>Security: This parser does NOT fetch any URLs. It does NOT execute
 * any scripts. It does NOT trust the parsed data as genuine — that is
 * the responsibility of the verification service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InfosysCredentialParserService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Known Infosys / Wingspan verification URL hosts
    private static final List<String> KNOWN_VERIFICATION_HOSTS = List.of(
            "verify.onwingspan.com",
            "validate.onwingspan.com",
            "onwingspan.com"
    );

    /**
     * Parses raw QR text into a structured credential.
     *
     * @param rawQrText the decoded QR code string
     * @return parsed credential (never null; warnings populated on partial parse)
     */
    public InfosysParsedCredential parse(String rawQrText) {
        if (rawQrText == null || rawQrText.isBlank()) {
            return InfosysParsedCredential.builder()
                    .qrContentType("UNKNOWN")
                    .parseWarnings(List.of("QR content is empty."))
                    .build();
        }

        String trimmed = rawQrText.trim();

        // Detect URL-type QR
        if (isUrl(trimmed)) {
            return parseUrlCredential(trimmed);
        }

        // Detect JSON-LD type
        if (trimmed.startsWith("{")) {
            return parseJsonLdCredential(trimmed);
        }

        // Detect JWT (three base64-separated sections)
        if (trimmed.split("\\.").length == 3 && !trimmed.contains(" ")) {
            return InfosysParsedCredential.builder()
                    .rawQrContent(trimmed)
                    .qrContentType("JWT")
                    .parseWarnings(List.of(
                            "QR content appears to be a JWT. JWT decoding is not yet supported. "
                            + "Please verify at the official Infosys portal."))
                    .build();
        }

        // Unknown format
        return InfosysParsedCredential.builder()
                .rawQrContent(trimmed)
                .qrContentType("UNKNOWN")
                .parseWarnings(List.of(
                        "QR content format is not recognised. Manual verification required."))
                .build();
    }

    // ── URL-type QR ───────────────────────────────────────────────────────────

    private InfosysParsedCredential parseUrlCredential(String url) {
        InfosysParsedCredential.InfosysParsedCredentialBuilder builder =
                InfosysParsedCredential.builder()
                        .rawQrContent(url)
                        .qrContentType("URL")
                        .officialVerificationUrl(url);

        List<String> warnings = new ArrayList<>();

        // Check if this is a known Infosys host
        boolean knownHost = KNOWN_VERIFICATION_HOSTS.stream()
                .anyMatch(h -> url.toLowerCase().contains(h));
        if (!knownHost) {
            warnings.add("QR URL does not point to a known Infosys verification host. "
                    + "Treat with caution.");
        }

        warnings.add("QR code contains a verification URL only. "
                + "Individual credential fields cannot be compared from a URL alone. "
                + "Please visit the URL to verify the certificate.");

        return builder.parseWarnings(warnings).build();
    }

    // ── JSON-LD Verifiable Credential ─────────────────────────────────────────

    private InfosysParsedCredential parseJsonLdCredential(String json) {
        List<String> warnings = new ArrayList<>();

        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse QR JSON content", e);
            return InfosysParsedCredential.builder()
                    .rawQrContent(json)
                    .qrContentType("JSON_LD")
                    .parseWarnings(List.of("QR JSON could not be parsed: " + e.getMessage()))
                    .build();
        }

        InfosysParsedCredential.InfosysParsedCredentialBuilder builder =
                InfosysParsedCredential.builder()
                        .rawQrContent(json)
                        .qrContentType("JSON_LD");

        // Credential ID
        builder.credentialId(textOrNull(root, "id"));

        // Credential types
        JsonNode typesNode = root.path("type");
        if (typesNode.isArray()) {
            List<String> types = new ArrayList<>();
            typesNode.forEach(t -> types.add(t.asText()));
            builder.credentialTypes(types);
        } else if (typesNode.isTextual()) {
            builder.credentialTypes(List.of(typesNode.asText()));
        }

        // Issuance date
        builder.issuanceDate(textOrNull(root, "issuanceDate"));

        // Issuer
        JsonNode issuerNode = root.path("issuer");
        if (issuerNode.isTextual()) {
            builder.issuer(issuerNode.asText());
        } else if (issuerNode.isObject()) {
            builder.issuer(textOrNull(issuerNode, "id"));
        }

        // Credential subject — may contain: issuedTo, name, training, course, etc.
        JsonNode subject = root.path("credentialSubject");
        if (!subject.isMissingNode()) {
            // Recipient name — try multiple known field names
            String recipientName = firstNonBlank(
                    textOrNull(subject, "issuedTo"),
                    textOrNull(subject, "name"),
                    textOrNull(subject, "recipientName"),
                    textOrNull(subject, "learnerName")
            );
            builder.issuedTo(recipientName);
            if (recipientName == null) {
                warnings.add("Could not determine recipient name from credentialSubject.");
            }

            // Completion date
            String compDate = firstNonBlank(
                    textOrNull(subject, "completionDate"),
                    textOrNull(subject, "courseCompletedOn"),
                    textOrNull(subject, "completedOn")
            );
            builder.completionDate(compDate);

            // Course name — may be nested under training / course
            String courseName = extractCourseName(subject, warnings);
            builder.courseName(courseName);

            // Course ID
            String courseId = extractCourseId(subject);
            builder.courseId(courseId);
        } else {
            warnings.add("credentialSubject block not found in QR credential.");
        }

        // Proof block
        JsonNode proof = root.path("proof");
        if (!proof.isMissingNode()) {
            builder.proofType(textOrNull(proof, "type"));
            builder.verificationMethod(textOrNull(proof, "verificationMethod"));
            builder.proofPurpose(textOrNull(proof, "proofPurpose"));
            builder.proofCreated(textOrNull(proof, "created"));

            // Check for proof value (jws, proofValue, signatureValue) — note only presence
            boolean hasProofValue =
                    proof.hasNonNull("jws")
                    || proof.hasNonNull("proofValue")
                    || proof.hasNonNull("signatureValue");
            builder.proofValuePresent(hasProofValue ? "YES" : "NO");

            if (!hasProofValue) {
                warnings.add("Proof block is present but contains no signature value.");
            }
        } else {
            warnings.add("No proof block found in QR credential. Signature cannot be assessed.");
            builder.proofValuePresent("NO");
        }

        // Derive official verification URL if not encoded directly
        String officialUrl = deriveVerificationUrl(root, warnings);
        builder.officialVerificationUrl(officialUrl);

        if (!warnings.isEmpty()) {
            builder.parseWarnings(warnings);
        }

        return builder.build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractCourseName(JsonNode subject, List<String> warnings) {
        // Direct field
        String direct = firstNonBlank(
                textOrNull(subject, "courseName"),
                textOrNull(subject, "course"),
                textOrNull(subject, "trainingName"),
                textOrNull(subject, "programName")
        );
        if (direct != null) return direct;

        // Nested under "training" object
        JsonNode training = subject.path("training");
        if (!training.isMissingNode() && training.isObject()) {
            String fromTraining = firstNonBlank(
                    textOrNull(training, "name"),
                    textOrNull(training, "courseName")
            );
            if (fromTraining != null) return fromTraining;
        }

        warnings.add("Could not determine course name from credentialSubject.");
        return null;
    }

    private String extractCourseId(JsonNode subject) {
        String direct = firstNonBlank(
                textOrNull(subject, "courseId"),
                textOrNull(subject, "contentId")
        );
        if (direct != null) return direct;

        JsonNode training = subject.path("training");
        if (!training.isMissingNode() && training.isObject()) {
            return textOrNull(training, "id");
        }
        return null;
    }

    private String deriveVerificationUrl(JsonNode root, List<String> warnings) {
        // Check top-level id if it's a URL
        String id = textOrNull(root, "id");
        if (id != null && isUrl(id)) return id;

        // Check issuer URL
        JsonNode issuerNode = root.path("issuer");
        if (issuerNode.isTextual() && isUrl(issuerNode.asText())) {
            String issuerUrl = issuerNode.asText();
            if (KNOWN_VERIFICATION_HOSTS.stream().anyMatch(h -> issuerUrl.contains(h))) {
                return issuerUrl;
            }
        }

        warnings.add("No official verification URL could be derived from the credential. "
                + "Please visit https://verify.onwingspan.com to verify manually.");
        return "https://verify.onwingspan.com";
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) return null;
        String text = child.asText().trim();
        return text.isEmpty() ? null : text;
    }

    @SuppressWarnings("java:S4784")
    private boolean isUrl(String s) {
        try {
            URI uri = new URI(s);
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}

