package com.aiplatform.starter.web.autoconfig;

import com.aiplatform.starter.web.interceptor.TenantInterceptor;
import com.aiplatform.starter.web.interceptor.UserContextInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-wires the web layer: CORS, multi-tenant interceptor, Result wrapper support.
 * Active by default; disable via {@code aiplatform.web.enabled=false}.
 */
@Configuration
@ComponentScan("com.aiplatform.starter.web")
@ConditionalOnProperty(name = "aiplatform.web.enabled", havingValue = "true", matchIfMissing = true)
public class WebAutoConfiguration implements WebMvcConfigurer {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "aiplatform.web.tenant-interceptor.enabled", havingValue = "true", matchIfMissing = true)
    public TenantInterceptor tenantInterceptor() {
        return new TenantInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    public UserContextInterceptor userContextInterceptor() {
        return new UserContextInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (tenantInterceptor() != null) {
            registry.addInterceptor(tenantInterceptor())
                    .addPathPatterns("/**")
                    .excludePathPatterns(
                            "/actuator/**",
                            "/v3/api-docs/**",
                            "/doc.html",
                            "/swagger-ui/**",
                            "/favicon.ico"
                    );
        }
        if (userContextInterceptor() != null) {
            registry.addInterceptor(userContextInterceptor())
                    .addPathPatterns("/**")
                    .excludePathPatterns(
                            "/actuator/**",
                            "/v3/api-docs/**",
                            "/doc.html",
                            "/swagger-ui/**",
                            "/favicon.ico",
                            "/api/auth/**"
                    );
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "X-Tenant-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
