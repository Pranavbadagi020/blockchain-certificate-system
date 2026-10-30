package certificate_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetCode;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class BlockchainService {

    private static final Logger log = LoggerFactory.getLogger(BlockchainService.class);

    private final Web3j web3j;
    private final String contractAddress;
    private final String issuerAddress;

    public BlockchainService(
            Web3j web3j,
            @Value("${app.blockchain.contract-address}") String contractAddress,
            @Value("${app.blockchain.issuer-address:0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266}") String issuerAddress) {
        this.web3j = web3j;
        this.contractAddress = contractAddress != null ? contractAddress.trim() : "";
        this.issuerAddress = issuerAddress != null ? issuerAddress.trim() : "";
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public String getIssuerAddress() {
        return issuerAddress;
    }

    public String getBlockchainVersion() throws IOException {
        return web3j.web3ClientVersion().send().getWeb3ClientVersion();
    }

    public BigInteger getChainId() throws IOException {
        return web3j.ethChainId().send().getChainId();
    }

    public boolean isContractDeployed() throws IOException {
        if (contractAddress == null || contractAddress.isBlank()) {
            return false;
        }

        EthGetCode response = web3j.ethGetCode(
                contractAddress,
                DefaultBlockParameterName.LATEST
        ).send();

        if (response.hasError()) {
            throw new IOException("Error querying contract code: " + response.getError().getMessage());
        }

        String code = response.getCode();
        return code != null && !code.equals("0x") && !code.equals("0x0") && !code.isEmpty();
    }

    public String getContractOwner() throws IOException {
        Function function = new Function(
                "owner",
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<Address>() {})
        );

        List<org.web3j.abi.datatypes.Type> result = callFunction(function);
        if (result.isEmpty()) {
            return null;
        }
        return (String) result.get(0).getValue();
    }

    public boolean isOperational() {
        try {
            return isContractDeployed() && getContractOwner() != null;
        } catch (Exception e) {
            log.debug("Blockchain operational check failed: {}", e.getMessage());
            return false;
        }
    }

    public String issueCertificate(String certificateId, String certificateHash)
            throws Exception {

        if (!isContractDeployed()) {
            throw new IOException(
                    "Smart contract is not deployed at address " + contractAddress
                            + ". Deploy the contract before issuing certificates."
            );
        }

        Function function = new Function(
                "issueCertificate",
                Arrays.asList(
                        new Utf8String(certificateId),
                        toBytes32(certificateHash)
                ),
                Collections.emptyList()
        );

        return sendTransaction(function);
    }

    public boolean verifyCertificate(String certificateId, String certificateHash)
            throws IOException {

        if (!isContractDeployed()) {
            throw new IOException(
                    "Smart contract is not deployed at address " + contractAddress
                            + ". Cannot verify certificate."
            );
        }

        Function function = new Function(
                "verifyCertificate",
                Arrays.asList(
                        new Utf8String(certificateId),
                        toBytes32(certificateHash)
                ),
                Collections.singletonList(new TypeReference<Bool>() {})
        );

        List<org.web3j.abi.datatypes.Type> result = callFunction(function);

        return !result.isEmpty() && (Boolean) result.get(0).getValue();
    }

    public String revokeCertificate(String certificateId) throws Exception {
        if (!isContractDeployed()) {
            throw new IOException(
                    "Smart contract is not deployed at address " + contractAddress
                            + ". Cannot revoke certificate."
            );
        }

        Function function = new Function(
                "revokeCertificate",
                Collections.singletonList(new Utf8String(certificateId)),
                Collections.emptyList()
        );

        return sendTransaction(function);
    }

    public CertificateChainDetails getCertificate(String certificateId)
            throws IOException {

        if (!isContractDeployed()) {
            throw new IOException(
                    "Smart contract is not deployed at address " + contractAddress
                            + " on the connected blockchain network."
            );
        }

        Function function = new Function(
                "getCertificate",
                Collections.singletonList(new Utf8String(certificateId)),
                Arrays.asList(
                        new TypeReference<Bytes32>() {},
                        new TypeReference<Address>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<Bool>() {}
                )
        );

        List<org.web3j.abi.datatypes.Type> result = callFunction(function);

        if (result.size() != 4) {
            throw new IOException(
                    "Could not decode certificate details from blockchain at "
                            + contractAddress + ". Expected 4 return values, received " + result.size()
            );
        }

        byte[] hashBytes = (byte[]) result.get(0).getValue();
        String hash = org.web3j.utils.Numeric.toHexStringNoPrefix(hashBytes);

        String issuer = (String) result.get(1).getValue();
        BigInteger issuedAt = (BigInteger) result.get(2).getValue();
        Boolean revoked = (Boolean) result.get(3).getValue();

        return new CertificateChainDetails(hash, issuer, issuedAt, revoked);
    }

    private List<org.web3j.abi.datatypes.Type> callFunction(Function function)
            throws IOException {

        String encodedFunction = FunctionEncoder.encode(function);

        Transaction transaction = Transaction.createEthCallTransaction(
                issuerAddress,
                contractAddress,
                encodedFunction
        );

        EthCall ethCall = web3j.ethCall(
                transaction,
                DefaultBlockParameterName.LATEST
        ).send();

        if (ethCall.hasError()) {
            throw new IOException("Blockchain eth_call error: " + ethCall.getError().getMessage());
        }

        String response = ethCall.getValue();

        if (response == null || response.equals("0x") || response.equals("0x0")) {
            return Collections.emptyList();
        }

        return FunctionReturnDecoder.decode(
                response,
                function.getOutputParameters()
        );
    }

    private String sendTransaction(Function function) throws Exception {
        String encodedFunction = FunctionEncoder.encode(function);

        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

        Transaction transaction = Transaction.createFunctionCallTransaction(
                issuerAddress,
                null,
                gasPrice,
                BigInteger.valueOf(3_000_000),
                contractAddress,
                BigInteger.ZERO,
                encodedFunction
        );

        EthSendTransaction response =
                web3j.ethSendTransaction(transaction).send();

        if (response.hasError()) {
            throw new IOException(
                    "Blockchain transaction failed: "
                            + response.getError().getMessage()
            );
        }

        String transactionHash = response.getTransactionHash();

        if (transactionHash == null || transactionHash.isBlank()) {
            throw new IOException("Blockchain did not return a transaction hash.");
        }

        for (int attempt = 0; attempt < 30; attempt++) {
            EthGetTransactionReceipt receiptResponse =
                    web3j.ethGetTransactionReceipt(transactionHash).send();

            Optional<TransactionReceipt> receipt =
                    receiptResponse.getTransactionReceipt();

            if (receipt.isPresent()) {
                if (!receipt.get().isStatusOK()) {
                    throw new IOException(
                            "Blockchain transaction reverted: " + transactionHash
                    );
                }

                return transactionHash;
            }

            Thread.sleep(1000);
        }

        throw new IOException(
                "Timed out waiting for blockchain transaction: " + transactionHash
        );
    }

    private Bytes32 toBytes32(String hash) {
        String normalized = hash.startsWith("0x") ? hash.substring(2) : hash;

        if (!normalized.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Certificate hash must contain exactly 64 hexadecimal characters."
            );
        }

        return new Bytes32(org.web3j.utils.Numeric.hexStringToByteArray(normalized));
    }

    public record CertificateChainDetails(
            String certificateHash,
            String issuer,
            BigInteger issuedAt,
            boolean revoked
    ) {}
}
