package com.example.booking.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor 
public class SecurityConfig {

    private final JwtProperties jwtProperties;

    @Bean 
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s-> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/payments/callback").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/venues/**","/api/courts/**").permitAll()
                        .requestMatchers("/actuator/health","/swagger-ui/**","/v3/api-docs/**").permitAll()
                        //khusus admin
                        .requestMatchers(HttpMethod.POST,"/api/venues/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,"/api/venues/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/courts/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey()).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean 
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

     @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean 
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private SecretKey secretKey() {
        return new SecretKeySpec(
                jwtProperties.secret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
    }
}
