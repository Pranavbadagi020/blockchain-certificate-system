package certificate_backend.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class SecurityConfig {

    private final String allowedOriginsStr;

    public SecurityConfig(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String allowedOriginsStr) {
        this.allowedOriginsStr = allowedOriginsStr;
    }

    @Bean
    @ConditionalOnBean(JwtTokenProvider.class)
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        return new JwtAuthenticationFilter(tokenProvider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Autowired(required = false) JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/blockchain/status").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/certificates/verify/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/certificates/verify/upload").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/certificates/verify/infosys").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/infosys/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()

                // Certificate issuance requires authenticated ISSUER or ADMIN
                .requestMatchers(HttpMethod.POST, "/api/certificates/issue")
                    .hasAnyRole("ADMIN", "ISSUER")

                // Revocation is strictly restricted to ADMIN
                .requestMatchers(HttpMethod.POST, "/api/certificates/revoke/**")
                    .hasRole("ADMIN")

                // Admin endpoints require ADMIN role
                .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")

                // Dashboard certificate list and user profile require authentication
                .requestMatchers(HttpMethod.GET, "/api/certificates")
                    .hasAnyRole("ADMIN", "ISSUER")
                .requestMatchers(HttpMethod.GET, "/api/auth/me")
                    .authenticated()

                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                )
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Access denied: insufficient permissions.\"}");
                })
            );

        if (jwtAuthenticationFilter != null) {
            http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOriginsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        configuration.setAllowedOrigins(origins);
        configuration.addAllowedOriginPattern("http://192.168.*:*");
        configuration.addAllowedOriginPattern("http://10.*:*");
        configuration.addAllowedOriginPattern("https://*.vercel.app");
        configuration.addAllowedOriginPattern("https://*.trycloudflare.com");
        configuration.addAllowedOriginPattern("https://*.ngrok-free.app");
        configuration.addAllowedOriginPattern("https://*.loca.lt");
        configuration.setAllowedMethods(
            List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
        );
        configuration.setAllowedHeaders(
            List.of("Content-Type", "Authorization", "X-Requested-With", "Accept")
        );
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/api/**", configuration);

        return source;
    }
}
