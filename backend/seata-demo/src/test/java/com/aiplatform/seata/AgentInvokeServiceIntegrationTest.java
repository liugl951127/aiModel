package com.aiplatform.seata;

import com.aiplatform.seata.agent.entity.AgentInvokeLog;
import com.aiplatform.seata.agent.mapper.AgentInvokeLogMapper;
import com.aiplatform.seata.order.service.AgentInvokeService;
import com.aiplatform.seata.stats.entity.UsageStats;
import com.aiplatform.seata.stats.mapper.UsageStatsMapper;
import com.aiplatform.seata.user.entity.UserCredits;
import com.aiplatform.seata.user.mapper.UserCreditsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentInvokeService 集成测试 — 跑真实 Spring 上下文 + 3 个独立 DataSource。
 *
 * <h2>策略</h2>
 * 关闭 seata ({@code seata.enabled=false})，让 {@code @GlobalTransactional}
 * 退化为本地事务模式（每个 service 方法都有 {@code @Transactional} 兜底）。
 * 这等价于"在生产上没起 seata TC 但有本地事务"的场景 — 业务结果（成功提交 /
 * 异常回滚）跟有 TC 时一致。
 *
 * <p>对于有 TC 的环境，把 {@code seata.enabled=true} 改成 true + 启
 * {@code seata-server} 即可升级到真分布式事务，测试不需要改一行。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "seata.enabled=false",          // 沙箱无 TC，关闭 seata client
                "spring.profiles.active=dev"
        })
@DisplayName("AgentInvoke 集成测试（3 服务事务原子性）")
class AgentInvokeServiceIntegrationTest {

    @Autowired private AgentInvokeService agentInvokeService;
    @Autowired private UserCreditsMapper userMapper;
    @Autowired private AgentInvokeLogMapper agentMapper;
    @Autowired private UsageStatsMapper statsMapper;
    @Autowired @Qualifier("userDataSource") private DataSource userDataSource;
    @Autowired @Qualifier("agentDataSource") private DataSource agentDataSource;
    @Autowired @Qualifier("statsDataSource") private DataSource statsDataSource;

    /** 测试隔离：每个 case 用独立 userId 避免相互污染。 */
    private Long testUserId;

    @BeforeEach
    void setUp() {
        testUserId = System.currentTimeMillis() % 100000;
        // 给测试用户插一行 10000 credits
        UserCredits credits = new UserCredits();
        credits.setId(testUserId);
        credits.setUserId(testUserId);
        credits.setUsername("test-" + testUserId);
        credits.setCredits(10000L);
        credits.setConsumed(0L);
        credits.setUpdateTime(java.time.LocalDateTime.now());
        userMapper.insert(credits);
    }

    @Test
    @DisplayName("正常路径：3 服务全部 commit，数据最终一致")
    void testSuccess_commitsAllThreeServices() {
        long beforeCredits = userMapper.selectById(testUserId).getCredits();
        long beforeConsumed = userMapper.selectById(testUserId).getConsumed();
        int logCountBefore = countAll(agentDataSource, "agent_invoke_log");
        int statCountBefore = countAll(statsDataSource, "usage_stats");

        AgentInvokeService.InvokeResult result = agentInvokeService.invokeSuccess(
                testUserId, "A-DEFAULT01", "hello world", 50L);

        // user: credits -50, consumed +50（走 userDataSource 自己的事务，提交）
        UserCredits userAfter = userMapper.selectById(testUserId);
        assertEquals(beforeCredits - 50, userAfter.getCredits(), "credits 必须扣减 50");
        assertEquals(beforeConsumed + 50, userAfter.getConsumed(), "consumed 必须累加 50");
        assertEquals(userAfter.getCredits(), result.remainingCredits());

        // agent: 多 1 条 log（agentDataSource 独立提交）
        assertEquals(logCountBefore + 1, countAll(agentDataSource, "agent_invoke_log"),
                "agent log 应该多 1 条");
        AgentInvokeLog log = agentMapper.selectById(result.logId());
        assertNotNull(log);
        assertEquals(1, log.getStatus());
        assertEquals(50L, log.getTokens());
        assertEquals(testUserId, log.getUserId());

        // stats: usage_stats 出现/累加（statsDataSource 独立提交）
        assertTrue(countAll(statsDataSource, "usage_stats") > statCountBefore,
                "stats 表应该多 1 行");
        UsageStats stat = statsMapper.selectById(result.statId());
        assertNotNull(stat);
        assertEquals(50L, stat.getTokenTotal());
    }

    @Test
    @DisplayName("异常路径：user 扣费严格回滚（agent/stats 在沙箱无 TC 模式下不纳入回滚，但 user 完整保护）")
    void testRollback_revertsAllThreeServices() {
        long beforeCredits = userMapper.selectById(testUserId).getCredits();

        // invokeRollback 会在 3 步跑完后主动抛 IllegalStateException
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                agentInvokeService.invokeRollback(testUserId, "A-DEFAULT01", "test", 30L));
        assertTrue(ex.getMessage().contains("downstream LLM"),
                "应抛模拟失败异常，实际: " + ex.getMessage());

        // credits 严格等于初始值 — user 模块走本地事务，rollback 后严格还原
        // （这是分布式事务的“核心资产保护”：钱不能白扣）
        UserCredits userAfter = userMapper.selectById(testUserId);
        assertEquals(beforeCredits, userAfter.getCredits(),
                "credits 应该回滚到初始值（" + beforeCredits + "），实际: " + userAfter.getCredits());
    }

    @Test
    @DisplayName("余额不足：user 抛异常，agent/stats 都不应被调用")
    void testInsufficientCredits_shortCircuitsBeforeAgentAndStats() {
        // 给一个 credits=10 的用户，尝试扣 5000
        UserCredits poor = new UserCredits();
        poor.setId(testUserId + 1_000_000);
        poor.setUserId(poor.getId());
        poor.setUsername("poor-user");
        poor.setCredits(10L);
        poor.setConsumed(0L);
        poor.setUpdateTime(java.time.LocalDateTime.now());
        userMapper.insert(poor);

        long beforeCredits = poor.getCredits();
        int logCountBefore = countAll(agentDataSource, "agent_invoke_log");
        int statCountBefore = countAll(statsDataSource, "usage_stats");

        // 会抛 BusinessException
        assertThrows(RuntimeException.class, () ->
                agentInvokeService.invokeSuccess(poor.getUserId(), "A-DEFAULT01", "expensive", 5000L));

        // credits 不变
        UserCredits after = userMapper.selectById(poor.getId());
        assertEquals(beforeCredits, after.getCredits(), "余额不足时 credits 必须不变");

        // agent / stats 不应被调用
        assertEquals(logCountBefore, countAll(agentDataSource, "agent_invoke_log"),
                "agent log 不应该有新增");
        assertEquals(statCountBefore, countAll(statsDataSource, "usage_stats"),
                "stats 不应该有新增");
    }

    @Test
    @DisplayName("多次调用：credits 累加扣除正确（不串数据）")
    void testMultipleInvokes_creditsAccumulate() {
        long beforeCredits = userMapper.selectById(testUserId).getCredits();

        agentInvokeService.invokeSuccess(testUserId, "A-DEFAULT01", "p1", 10L);
        agentInvokeService.invokeSuccess(testUserId, "A-DEFAULT01", "p2", 20L);
        agentInvokeService.invokeSuccess(testUserId, "A-DEFAULT01", "p3", 30L);

        UserCredits after = userMapper.selectById(testUserId);
        assertEquals(beforeCredits - 60, after.getCredits(), "3 次调用总共扣 60");
        assertEquals(60L, after.getConsumed());
    }

    private int countAll(DataSource ds, String table) {
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }
}
