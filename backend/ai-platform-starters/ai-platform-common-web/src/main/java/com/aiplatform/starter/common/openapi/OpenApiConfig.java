package com.aiplatform.starter.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通用 OpenAPI/Swagger UI 配置 (Knife4j 增强).
 *
 * <p>通过 common-web starter 自动给所有 servlet 服务开启：
 * <ul>
 *   <li>UI 入口：/doc.html (Knife4j 增强版) + /swagger-ui/index.html (原生)</li>
 *   <li>OpenAPI JSON：/v3/api-docs</li>
 *   <li>OpenAPI YAML：/v3/api-docs.yaml</li>
 * </ul>
 *
 * <p>gateway 走 webflux 反应式栈，本配置不生效 (knife4j starter 会因
 * reactive 不存在而 ConditionalOnClass 跳过)。</p>
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Bean
    public OpenAPI platformOpenAPI() {
        String title = "AI Agent Platform - " + serviceName;
        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .description("AI Agent Platform 业务接口文档\n\n" +
                                "- 项目地址: https://github.com/liugl951127/aiModel\n" +
                                "- 技术栈: Spring Cloud Alibaba 2023.0.1 + Spring Boot 3.2.5\n" +
                                "- 鉴权: 除 /api/auth/login 等白名单接口外, 需在右上角 Authorize 输入 Bearer token\n" +
                                "- 多租户: tenantId 走 gateway 注入的 X-Tenant-Id header")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("AI Platform Team")
                                .email("admin@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .in(SecurityScheme.In.HEADER)
                                        .name("Authorization")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
