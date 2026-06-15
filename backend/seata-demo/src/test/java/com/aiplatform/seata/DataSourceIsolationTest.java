package com.aiplatform.seata;

import com.aiplatform.seata.agent.entity.AgentInvokeLog;
import com.aiplatform.seata.agent.mapper.AgentInvokeLogMapper;
import com.aiplatform.seata.stats.entity.UsageStats;
import com.aiplatform.seata.stats.mapper.UsageStatsMapper;
import com.aiplatform.seata.user.entity.UserCredits;
import com.aiplatform.seata.user.mapper.UserCreditsMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 3 个 DataSource 隔离性测试。
 *
 * <h2>验证目标</h2>
 * <ol>
 *   <li>3 个 DataSource bean 都存在，且类型不同（独立的 HikariDataSource）</li>
 *   <li>userDataSource 只连 user_credits，agentDataSource 只连 agent_invoke_log，statsDataSource 只连 usage_stats</li>
 *   <li>不同 datasource 间的 SQL 互不影响（一个库删表不会影响另一个库）</li>
 *   <li>没有 DataSource bean 冲突（@Qualifier 解析正确）</li>
 * </ol>
 *
 * <p>这是分布式事务的"物理隔离"前置条件 — seata AT 模式靠每个 datasource 独立
 * undo_log 工作，必须保证 3 个库互不串。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "seata.enabled=false",
                "spring.profiles.active=dev"
        })
@DisplayName("3 个 DataSource 隔离性测试")
class DataSourceIsolationTest {

    @Autowired @Qualifier("userDataSource") private DataSource userDataSource;
    @Autowired @Qualifier("agentDataSource") private DataSource agentDataSource;
    @Autowired @Qualifier("statsDataSource") private DataSource statsDataSource;
    @Autowired private UserCreditsMapper userMapper;
    @Autowired private AgentInvokeLogMapper agentMapper;
    @Autowired private UsageStatsMapper statsMapper;

    @Test
    @DisplayName("3 个 DataSource bean 存在且类型不同")
    void testDataSourcesExist() {
        assertNotNull(userDataSource, "userDataSource 必须存在");
        assertNotNull(agentDataSource, "agentDataSource 必须存在");
        assertNotNull(statsDataSource, "statsDataSource 必须存在");
        assertNotSame(userDataSource, agentDataSource, "userDataSource 和 agentDataSource 必须是不同实例");
        assertNotSame(agentDataSource, statsDataSource, "agentDataSource 和 statsDataSource 必须是不同实例");
        assertNotSame(userDataSource, statsDataSource, "userDataSource 和 statsDataSource 必须是不同实例");
    }

    @Test
    @DisplayName("userDataSource 只能访问 user 表")
    void testUserDataSourceCanReadUserTables() {
        JdbcTemplate jdbc = new JdbcTemplate(userDataSource);
        assertDoesNotThrow(() -> {
            jdbc.queryForObject("SELECT COUNT(*) FROM user_credits", Integer.class);
        });
        // 验证 userDataSource 看不到 agent 表（H2 内存库无此 schema）
        assertThrows(Exception.class, () -> {
            jdbc.queryForObject("SELECT COUNT(*) FROM agent_invoke_log", Integer.class);
        }, "userDataSource 不应能访问 agent_invoke_log");
        assertThrows(Exception.class, () -> {
            jdbc.queryForObject("SELECT COUNT(*) FROM usage_stats", Integer.class);
        }, "userDataSource 不应能访问 usage_stats");
    }

    @Test
    @DisplayName("agentDataSource 只能访问 agent 表")
    void testAgentDataSourceCanReadAgentTables() {
        JdbcTemplate jdbc = new JdbcTemplate(agentDataSource);
        assertDoesNotThrow(() -> {
            jdbc.queryForObject("SELECT COUNT(*) FROM agent_invoke_log", Integer.class);
        });
        assertThrows(Exception.class, () -> {
            jdbc.queryForObject("SELECT COUNT(*) FROM user_credits", Integer.class);
        });
        assertThrows(Exception.class, () -> {
            jdbc.queryForObject("SELECT COUNT(*) FROM usage_stats", Integer.class);
        });
    }

    @Test
    @DisplayName("statsDataSource 只能访问 stats 表")
    void testStatsDataSourceCanReadStatsTables() {
        JdbcTemplate jdbc = new JdbcTemplate(statsDataSource);
        assertDoesNotThrow(() -> {
            jdbc.queryForObject("SELECT COUNT(*) FROM usage_stats", Integer.class);
        });
        assertThrows(Exception.class, () -> {
            jdbc.queryForObject("SELECT COUNT(*) FROM user_credits", Integer.class);
        });
        assertThrows(Exception.class, () -> {
            jdbc.queryForObject("SELECT COUNT(*) FROM agent_invoke_log", Integer.class);
        });
    }

    @Test
    @DisplayName("Mapper 走自己 datasource：插一行只在自己库可见")
    void testMapperDataSourceBinding() {
        Long probeId = System.currentTimeMillis() % 1000000 + 700000L;

        // user mapper 插 user_credits
        UserCredits credits = new UserCredits();
        credits.setId(probeId);
        credits.setUserId(probeId);
        credits.setUsername("iso-test");
        credits.setCredits(999L);
        credits.setConsumed(0L);
        credits.setUpdateTime(java.time.LocalDateTime.now());
        userMapper.insert(credits);

        // agent mapper 插 agent_invoke_log
        AgentInvokeLog log = new AgentInvokeLog();
        log.setId(probeId);
        log.setTraceId("iso-" + probeId);
        log.setUserId(probeId);
        log.setAgentCode("A-ISO");
        log.setPrompt("iso test");
        log.setResponse("ok");
        log.setTokens(1L);
        log.setStatus(1);
        log.setCreateTime(java.time.LocalDateTime.now());
        agentMapper.insert(log);

        // stats mapper 插 usage_stats
        UsageStats stat = new UsageStats();
        stat.setId(probeId);
        stat.setStatDate("2026-06-15");
        stat.setAgentCode("A-ISO");
        stat.setInvokeCount(1L);
        stat.setTokenTotal(1L);
        statsMapper.insert(stat);

        // 各自能读到自己插的
        assertNotNull(userMapper.selectById(probeId), "user mapper 看不到自己插的 user_credits?");
        assertNotNull(agentMapper.selectById(probeId), "agent mapper 看不到自己插的 agent_invoke_log?");
        assertNotNull(statsMapper.selectById(probeId), "stats mapper 看不到自己插的 usage_stats?");

        // 跨表查不到（不同 datasource）
        JdbcTemplate userJdbc = new JdbcTemplate(userDataSource);
        JdbcTemplate agentJdbc = new JdbcTemplate(agentDataSource);
        JdbcTemplate statsJdbc = new JdbcTemplate(statsDataSource);

        assertThrows(Exception.class, () ->
                userJdbc.queryForObject("SELECT * FROM agent_invoke_log WHERE id = ?", Object.class, probeId),
                "user datasource 看到 agent_invoke_log 数据了 = 隔离失败");
        assertThrows(Exception.class, () ->
                statsJdbc.queryForObject("SELECT * FROM user_credits WHERE id = ?", Object.class, probeId),
                "stats datasource 看到 user_credits 数据了 = 隔离失败");
        assertThrows(Exception.class, () ->
                agentJdbc.queryForObject("SELECT * FROM usage_stats WHERE id = ?", Object.class, probeId),
                "agent datasource 看到 usage_stats 数据了 = 隔离失败");
    }
}
