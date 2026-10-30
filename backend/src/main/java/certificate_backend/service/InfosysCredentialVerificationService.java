package certificate_backend.service;

import certificate_backend.dto.InfosysParsedCredential;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Assesses the cryptographic signature and issuer trust status of an
 * Infosys Springboard Verifiable Credential.
 *
 * <h2>Current limitations — read before modifying</h2>
 * <p>Infosys Springboard uses <strong>Ed25519Signature2018</strong> with a
 * <strong>did:india</strong> verification method.  Infosys does NOT publish:
 * <ul>
 *   <li>A public key endpoint for {@code did:india}</li>
 *   <li>A documented third-party REST API</li>
 *   <li>An open-source verification library</li>
 * </ul>
 * <p>Therefore <strong>independent cryptographic signature verification is not
 * possible</strong>.  This service performs structural validation only and
 * always returns {@code UNABLE_TO_VERIFY_NO_PUBLIC_KEY} for signature status.
 *
 * <p>If a future Infosys public API or DID resolver becomes available, replace
 * {@link #assessSignature} with a real implementation and update
 * {@code signatureVerificationStatus} accordingly.
 *
 * <h2>What this service DOES check</h2>
 * <ul>
 *   <li>Proof block presence and structural completeness</li>
 *   <li>Recognised proof type</li>
 *   <li>Verification method plausibility (did:india / onwingspan URL)</li>
 *   <li>Credential type recognition</li>
 * </ul>
 */
@Slf4j
@Service
public class InfosysCredentialVerificationService {

    private static final List<String> SUPPORTED_PROOF_TYPES = List.of(
            "Ed25519Signature2018",
            "Ed25519Signature2020",
            "RsaSignature2018",
            "JsonWebSignature2020"
    );

    private static final List<String> KNOWN_VERIFICATION_METHODS = List.of(
            "did:india",
            "onwingspan.com",
            "wingspan"
    );

    /**
     * Represents the outcome of structural + availability assessment.
     */
    public enum SignatureStatus {
        /** No public key available; structural check passed. Cannot verify. */
        UNABLE_TO_VERIFY_NO_PUBLIC_KEY,
        /** Proof block is missing entirely. */
        NO_PROOF_PRESENT,
        /** Proof block exists but is structurally incomplete/malformed. */
        STRUCTURALLY_INVALID,
        /** Proof type not recognised by this system. */
        UNSUPPORTED_PROOF_TYPE,
        /** QR is a URL; credential data not available locally. */
        URL_TYPE_NO_CREDENTIAL_DATA
    }

    public record AssessmentResult(
            SignatureStatus signatureStatus,
            boolean issuerTrusted,
            String issuerTrustNote,
            java.util.List<String> structuralWarnings
    ) {}

    /**
     * Assesses the credential structurally and returns an honest status.
     *
     * @param credential the parsed credential
     * @return assessment result — never claims the signature is valid
     */
    public AssessmentResult assessSignature(InfosysParsedCredential credential) {
        java.util.List<String> warnings = new java.util.ArrayList<>();

        // URL-type QR — no embedded credential data
        if ("URL".equals(credential.getQrContentType())) {
            return new AssessmentResult(
                    SignatureStatus.URL_TYPE_NO_CREDENTIAL_DATA,
                    false,
                    "Issuer identity cannot be established from a URL-only QR code.",
                    List.of("QR contains a URL; credential data is not embedded locally.")
            );
        }

        // JWT — not yet supported
        if ("JWT".equals(credential.getQrContentType())) {
            return new AssessmentResult(
                    SignatureStatus.UNSUPPORTED_PROOF_TYPE,
                    false,
                    "JWT credential format is not yet supported for structural assessment.",
                    List.of("JWT credential format not yet supported.")
            );
        }

        // UNKNOWN format
        if ("UNKNOWN".equals(credential.getQrContentType())) {
            return new AssessmentResult(
                    SignatureStatus.STRUCTURALLY_INVALID,
                    false,
                    "QR content format is unrecognised.",
                    List.of("QR content format is unrecognised. Cannot assess proof.")
            );
        }

        // JSON-LD credential — structural checks
        String proofType = credential.getProofType();
        String verificationMethod = credential.getVerificationMethod();
        String proofValuePresent = credential.getProofValuePresent();

        // Check proof block presence
        if (proofType == null && verificationMethod == null && "NO".equals(proofValuePresent)) {
            return new AssessmentResult(
                    SignatureStatus.NO_PROOF_PRESENT,
                    false,
                    "No proof block present in the credential.",
                    List.of("Credential has no proof block.")
            );
        }

        // Check proof type support
        if (proofType != null && SUPPORTED_PROOF_TYPES.stream()
                .noneMatch(p -> p.equalsIgnoreCase(proofType))) {
            warnings.add("Proof type '" + proofType + "' is not in the list of recognised types.");
            return new AssessmentResult(
                    SignatureStatus.UNSUPPORTED_PROOF_TYPE,
                    false,
                    "Proof type '" + proofType + "' is not supported.",
                    warnings
            );
        }

        // Check for proof value
        if (!"YES".equals(proofValuePresent)) {
            warnings.add("Proof block is present but the signature value (jws/proofValue) is absent.");
            return new AssessmentResult(
                    SignatureStatus.STRUCTURALLY_INVALID,
                    false,
                    "Proof block has no signature value.",
                    warnings
            );
        }

        // Assess verification method plausibility
        boolean plausibleMethod = verificationMethod != null && KNOWN_VERIFICATION_METHODS.stream()
                .anyMatch(m -> verificationMethod.toLowerCase().contains(m.toLowerCase()));
        if (!plausibleMethod && verificationMethod != null) {
            warnings.add("Verification method '" + verificationMethod
                    + "' is not a known Infosys method. Treat with caution.");
        }

        // Structural check passed — but no cryptographic verification possible
        warnings.add("Ed25519 signature CANNOT be independently verified: "
                + "Infosys does not publish a public key for the '" + verificationMethod
                + "' verification method. Please confirm at https://verify.onwingspan.com");

        return new AssessmentResult(
                SignatureStatus.UNABLE_TO_VERIFY_NO_PUBLIC_KEY,
                false, // never trust the issuer without a verified key anchor
                "Issuer identity cannot be cryptographically confirmed. "
                + "No public key is available for the '" + verificationMethod + "' method.",
                warnings
        );
    }
}

