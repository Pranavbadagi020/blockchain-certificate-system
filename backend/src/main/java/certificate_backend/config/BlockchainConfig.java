package certificate_backend.config;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.util.concurrent.TimeUnit;

@Configuration
public class BlockchainConfig {

    @Value("${app.blockchain.rpc-url}")
    private String rpcUrl;

    @Value("${app.blockchain.timeout-seconds:10}")
    private long timeoutSeconds;

    @Bean
    public Web3j web3j() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
        return Web3j.build(new HttpService(rpcUrl, client));
    }
}
