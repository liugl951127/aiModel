package com.aiplatform.starter.mybatis.autoconfig;

import com.aiplatform.starter.mybatis.autofill.MybatisAutoFillHandler;
import com.aiplatform.starter.mybatis.tenant.MybatisPlusTenantHandler;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the MyBatis-Plus plugin chain and the auto-fill handler.
 * Becomes active when MyBatis-Plus is on the classpath and {@code aiplatform.mybatis.enabled=true}.
 */
@Configuration
@ConditionalOnClass(MybatisPlusInterceptor.class)
@ConditionalOnProperty(name = "aiplatform.mybatis.enabled", havingValue = "true", matchIfMissing = true)
public class MybatisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new MybatisPlusTenantHandler()));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean
    public MetaObjectHandler metaObjectHandler() {
        return new MybatisAutoFillHandler();
    }
}
