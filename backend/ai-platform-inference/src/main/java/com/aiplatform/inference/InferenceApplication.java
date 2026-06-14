package com.aiplatform.inference;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.context.annotation.Bean;

/**
 * Inference service. Runs as a thin standalone microservice - no DB, no Redis required.
 * It is typically called from the agent service via Feign, so security is wide open.
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
@ComponentScan(
        basePackages = {
                "com.aiplatform.inference",
                "com.aiplatform.starter.web",
                "com.aiplatform.starter.secure",
                "com.aiplatform.starter.redis"
        },
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "com\\.aiplatform\\.starter\\.web\\.interceptor\\.TenantInterceptor"),
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "com\\.aiplatform\\.starter\\.web\\.interceptor\\.UserContextInterceptor")
        }
)
public class InferenceApplication {

    @Bean
    public SecurityFilterChain openSecurity(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    public static void main(String[] args) {
        SpringApplication.run(InferenceApplication.class, args);
    }
}
