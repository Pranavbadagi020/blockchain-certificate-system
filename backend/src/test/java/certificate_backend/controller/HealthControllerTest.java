package certificate_backend.controller;

import certificate_backend.security.SecurityConfig;
import certificate_backend.service.BlockchainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigInteger;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
@DisplayName("HealthController")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MongoTemplate mongoTemplate;

    @MockitoBean
    private BlockchainService blockchainService;

    @Test
    @DisplayName("GET /api/health returns detailed diagnostic payload with UP status when all subsystems operational")
    void healthReturnsDetailedPayloadWhenHealthy() throws Exception {
        when(blockchainService.getBlockchainVersion()).thenReturn("HardhatNetwork/2.22.0");
        when(blockchainService.getChainId()).thenReturn(BigInteger.valueOf(31337));
        when(blockchainService.isContractDeployed()).thenReturn(true);
        when(blockchainService.getContractOwner()).thenReturn("0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266");
        when(blockchainService.getContractAddress()).thenReturn("0x5FbDB2315678afecb367f032d93F642f64180aa3");

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application.status").value("UP"))
                .andExpect(jsonPath("$.blockchain.rpcConnected").value(true))
                .andExpect(jsonPath("$.blockchain.chainId").value(31337))
                .andExpect(jsonPath("$.blockchain.contractDeployed").value(true))
                .andExpect(jsonPath("$.blockchain.verificationOperational").value(true))
                .andExpect(jsonPath("$.blockchain.contractAddress").value("0x5FbDB2315678afecb367f032d93F642f64180aa3"));
    }

    @Test
    @DisplayName("GET /api/health reports DEGRADED when smart contract is not deployed")
    void healthReportsDegradedWhenContractNotDeployed() throws Exception {
        when(blockchainService.getBlockchainVersion()).thenReturn("HardhatNetwork/2.22.0");
        when(blockchainService.getChainId()).thenReturn(BigInteger.valueOf(31337));
        when(blockchainService.isContractDeployed()).thenReturn(false);
        when(blockchainService.getContractAddress()).thenReturn("0x5FbDB2315678afecb367f032d93F642f64180aa3");

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.blockchain.rpcConnected").value(true))
                .andExpect(jsonPath("$.blockchain.contractDeployed").value(false))
                .andExpect(jsonPath("$.blockchain.verificationOperational").value(false));
    }
}
