package com.aiplatform.starter.secure.autoconfig;

import com.aiplatform.common.util.JwtUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Builds the {@link JwtUtils} singleton from {@code aiplatform.jwt.*} properties.
 */
@Configuration
@EnableConfigurationProperties(SecureAutoConfiguration.JwtProperties.class)
@ConditionalOnProperty(name = "aiplatform.jwt.enabled", havingValue = "true", matchIfMissing = true)
public class SecureAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtUtils jwtUtils(JwtProperties props) {
        return new JwtUtils(props.getSecret(), props.getExpiration(), props.getIssuer());
    }

    @ConfigurationProperties(prefix = "aiplatform.jwt")
    public static class JwtProperties {
        private String secret = "ai-platform-default-secret-key-please-change-32+";
        private long expiration = 24L * 60L * 60L * 1000L;
        private String issuer = "ai-platform";

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getExpiration() { return expiration; }
        public void setExpiration(long expiration) { this.expiration = expiration; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
    }
}
