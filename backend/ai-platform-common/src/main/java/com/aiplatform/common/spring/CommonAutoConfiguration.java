package com.aiplatform.common.spring;

import com.aiplatform.common.tenant.MybatisPlusTenantHandler;
import com.aiplatform.common.tenant.TenantInterceptor;
import com.aiplatform.common.util.JwtUtils;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configuration exposed by common module. Any service that depends on
 * ai-platform-common picks up the global web setup, MP plugins and tenant interceptor.
 */
@Configuration
@ComponentScan("com.aiplatform.common")
@EnableConfigurationProperties
@RequiredArgsConstructor
public class CommonAutoConfiguration implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;
    private final JwtUtils jwtUtils;

    @Bean
    @ConditionalOnMissingBean
    public com.baomidou.mybatisplus.core.handlers.MetaObjectHandler metaObjectHandler() {
        return new MybatisAutoFillHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new MybatisPlusTenantHandler()));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/**");
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
