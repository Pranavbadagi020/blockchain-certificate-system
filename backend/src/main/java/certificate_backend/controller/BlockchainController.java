package certificate_backend.controller;

import certificate_backend.dto.CertificateResponse;
import certificate_backend.service.BlockchainService;
import certificate_backend.service.CertificateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/blockchain")
public class BlockchainController {

    private final BlockchainService blockchainService;
    private final CertificateService certificateService;
    private final long expectedChainId;

    public BlockchainController(
            BlockchainService blockchainService,
            CertificateService certificateService,
            @Value("${app.blockchain.expected-chain-id:31337}") long expectedChainId) {
        this.blockchainService = blockchainService;
        this.certificateService = certificateService;
        this.expectedChainId = expectedChainId;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getBlockchainStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        try {
            String version = blockchainService.getBlockchainVersion();
            BigInteger chainId = blockchainService.getChainId();
            boolean deployed = blockchainService.isContractDeployed();
            String owner = null;
            if (deployed) {
                try {
                    owner = blockchainService.getContractOwner();
                } catch (Exception ignored) {}
            }

            boolean operational = deployed && owner != null;

            status.put("connected", true);
            status.put("blockchainVersion", version);
            status.put("chainId", chainId);
            status.put("expectedChainId", expectedChainId);
            status.put("contractAddress", blockchainService.getContractAddress());
            status.put("contractDeployed", deployed);
            status.put("contractOwner", owner != null ? owner : "UNKNOWN");
            status.put("verificationOperational", operational);

            return ResponseEntity.ok(status);
        } catch (Exception exception) {
            status.put("connected", false);
            status.put("error", "Unable to connect to blockchain: " + exception.getMessage());
            status.put("contractAddress", blockchainService.getContractAddress());
            status.put("contractDeployed", false);
            status.put("verificationOperational", false);

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(status);
        }
    }

    /**
     * Re-registers a single certificate onto the blockchain.
     *
     * <p>Use this when Hardhat was restarted and a specific certificate's on-chain
     * record was lost. Requires admin authentication.
     *
     * <pre>POST /api/blockchain/reregister/{certificateId}</pre>
     */
    @PostMapping("/reregister/{certificateId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CertificateResponse> reRegisterCertificate(
            @PathVariable String certificateId) {
        try {
            CertificateResponse response = certificateService.reRegisterToBlockchain(certificateId);
            return ResponseEntity.ok(response);
        } catch (certificate_backend.exception.CertificateNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Re-registers ALL certificates in MongoDB that are missing from the blockchain.
     *
     * <p>Use this after a Hardhat node restart to restore all on-chain records.
     * Requires admin authentication.
     *
     * <pre>POST /api/blockchain/reregister-all</pre>
     */
    @PostMapping("/reregister-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> reRegisterAll() {
        try {
            List<Map<String, Object>> results = certificateService.reRegisterAllToBlockchain();

            long reRegistered = results.stream()
                    .filter(r -> "RE_REGISTERED".equals(r.get("status"))).count();
            long alreadyOnChain = results.stream()
                    .filter(r -> "ALREADY_ON_CHAIN".equals(r.get("status"))).count();
            long failed = results.stream()
                    .filter(r -> "FAILED".equals(r.get("status"))).count();
            long skipped = results.stream()
                    .filter(r -> "SKIPPED".equals(r.get("status"))).count();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("total", results.size());
            response.put("reRegistered", reRegistered);
            response.put("alreadyOnChain", alreadyOnChain);
            response.put("failed", failed);
            response.put("skipped", skipped);
            response.put("details", results);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Re-registration failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
