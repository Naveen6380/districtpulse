package org.example.districtpulse.security;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean SecretKeySpec jwtKey(@Value("${app.jwt-secret}") String secret) {
        if(secret.getBytes(StandardCharsets.UTF_8).length<32)
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes; configure a random secret");
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256");
    }
    @Bean JwtEncoder encoder(SecretKeySpec key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean JwtDecoder decoder(SecretKeySpec key) {
        NimbusJwtDecoder decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("districtpulse"));
        return decoder;
    }
    @Bean PasswordEncoder passwords(){ return new BCryptPasswordEncoder(12); }
    @Bean SecurityFilterChain security(HttpSecurity http,UserRepository users) throws Exception {
        JwtGrantedAuthoritiesConverter grants=new JwtGrantedAuthoritiesConverter();
        grants.setAuthoritiesClaimName("role"); grants.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter=new JwtAuthenticationConverter(); converter.setJwtGrantedAuthoritiesConverter(grants);
        return http.cors(c->{}).csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.requestMatchers("/api/auth/login","/api/auth/forgot-password","/api/health").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter)))
            .addFilterAfter(new ActiveUserFilter(users),BearerTokenAuthenticationFilter.class).build();
    }
}
