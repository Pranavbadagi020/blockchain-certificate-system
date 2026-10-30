package certificate_backend.controller;

import certificate_backend.service.BlockchainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final MongoTemplate mongoTemplate;
    private final BlockchainService blockchainService;
    private final String appName;
    private final long expectedChainId;

    public HealthController(
            @Autowired(required = false) MongoTemplate mongoTemplate,
            @Autowired(required = false) BlockchainService blockchainService,
            @Value("${spring.application.name:certificate-backend}") String appName,
            @Value("${app.blockchain.expected-chain-id:31337}") long expectedChainId) {
        this.mongoTemplate = mongoTemplate;
        this.blockchainService = blockchainService;
        this.appName = appName;
        this.expectedChainId = expectedChainId;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("timestamp", Instant.now().toString());

        Map<String, Object> appDetails = new LinkedHashMap<>();
        appDetails.put("status", "UP");
        appDetails.put("name", appName);
        response.put("application", appDetails);

        // Database status
        Map<String, Object> dbDetails = new LinkedHashMap<>();
        if (mongoTemplate != null) {
            try {
                mongoTemplate.executeCommand("{ ping: 1 }");
                dbDetails.put("status", "CONNECTED");
                dbDetails.put("type", "MongoDB");
                if (mongoTemplate.getDb() != null) {
                    dbDetails.put("database", mongoTemplate.getDb().getName());
                }
            } catch (Exception e) {
                dbDetails.put("status", "DISCONNECTED");
                dbDetails.put("error", e.getMessage());
                response.put("status", "DEGRADED");
            }
        } else {
            dbDetails.put("status", "UNKNOWN");
        }
        response.put("database", dbDetails);

        // Blockchain status
        Map<String, Object> bcDetails = new LinkedHashMap<>();
        if (blockchainService != null) {
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

                bcDetails.put("rpcConnected", true);
                bcDetails.put("clientVersion", version);
                bcDetails.put("chainId", chainId);
                bcDetails.put("expectedChainId", expectedChainId);
                bcDetails.put("chainIdMatches", chainId != null && chainId.longValue() == expectedChainId);
                bcDetails.put("contractAddress", blockchainService.getContractAddress());
                bcDetails.put("contractDeployed", deployed);
                bcDetails.put("contractOwner", owner != null ? owner : "UNKNOWN");
                bcDetails.put("verificationOperational", operational);

                if (!operational) {
                    response.put("status", "DEGRADED");
                }
            } catch (Exception e) {
                bcDetails.put("rpcConnected", false);
                bcDetails.put("error", e.getMessage());
                bcDetails.put("contractAddress", blockchainService.getContractAddress());
                bcDetails.put("verificationOperational", false);
                response.put("status", "DEGRADED");
            }
        } else {
            bcDetails.put("rpcConnected", false);
            bcDetails.put("status", "SERVICE_NOT_INITIALIZED");
            bcDetails.put("verificationOperational", false);
        }
        response.put("blockchain", bcDetails);

        return ResponseEntity.ok(response);
    }
}
