package com.aiplatform.seata.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import io.seata.rm.datasource.DataSourceProxy;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * 3 个独立 DataSource 配置。
 *
 * <h2>seata AT 模式自动 wrap</h2>
 * 用了 {@code seata-spring-boot-starter} 后，
 * {@code SeataDataSourceAutoConfiguration} 会自动把任何 {@link DataSource} bean
 * 包成 {@link DataSourceProxy}（前提是 {@code seata.enabled=true}）。
 * 所以这里**只**注册 3 个裸 DataSource（用 {@code userDataSource / agentDataSource /
 * statsDataSource} 命名），seata 启动后会自动给它们挂代理 —
 * 最终 {@code userDataSourceProxy} 等同于我们手写的 wrapper。
 *
 * <p>注意 bean name 必须带后缀 {@code DataSourceProxy}，seata 默认 wrap 规则
 * 是 "原名 + DataSourceProxy" 后缀。</p>
 */
@Configuration
public class DataSourceConfig {

    // ===================== user =====================
    @Primary
    @Bean(name = "userDataSource")
    public DataSource userDataSource() {
        return DataSourceHelper.build(
                "jdbc:h2:mem:userdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=RUNSCRIPT FROM 'classpath:user-init.sql'");
    }

    @Primary
    @Bean
    public SqlSessionFactory userSqlSessionFactory(@org.springframework.beans.factory.annotation.Qualifier("userDataSource") DataSource ds) throws Exception {
        return buildFactory(ds, "classpath*:mapper/user/**/*.xml");
    }

    // ===================== agent =====================
    @Bean(name = "agentDataSource")
    public DataSource agentDataSource() {
        return DataSourceHelper.build(
                "jdbc:h2:mem:agentdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=RUNSCRIPT FROM 'classpath:agent-init.sql'");
    }

    @Bean
    public SqlSessionFactory agentSqlSessionFactory(@org.springframework.beans.factory.annotation.Qualifier("agentDataSource") DataSource ds) throws Exception {
        return buildFactory(ds, "classpath*:mapper/agent/**/*.xml");
    }

    // ===================== stats =====================
    @Bean(name = "statsDataSource")
    public DataSource statsDataSource() {
        return DataSourceHelper.build(
                "jdbc:h2:mem:statsdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=RUNSCRIPT FROM 'classpath:stats-init.sql'");
    }

    @Bean
    public SqlSessionFactory statsSqlSessionFactory(@org.springframework.beans.factory.annotation.Qualifier("statsDataSource") DataSource ds) throws Exception {
        return buildFactory(ds, "classpath*:mapper/stats/**/*.xml");
    }

    private SqlSessionFactory buildFactory(DataSource ds, String mapperLocation) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(cfg);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(mapperLocation));
        return factory.getObject();
    }
}
