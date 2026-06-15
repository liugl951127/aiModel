package com.aiplatform.seata.stats.service;

import com.aiplatform.seata.stats.entity.UsageStats;
import com.aiplatform.seata.stats.mapper.UsageStatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 调用统计服务 — 按天 + agentCode 累加计数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final UsageStatsMapper mapper;

    public Long increment(String agentCode, Long tokens) {
        log.info("[stats-service] increment agentCode={} tokens={}", agentCode, tokens);
        String date = LocalDate.now().toString();
        Long id = mapper.findId(date, agentCode);
        if (id == null) {
            UsageStats row = new UsageStats();
            row.setStatDate(date);
            row.setAgentCode(agentCode);
            row.setInvokeCount(1L);
            row.setTokenTotal(tokens);
            mapper.insert(row);
            log.info("[stats-service] insert new row id={}", row.getId());
            return row.getId();
        }
        mapper.increment(id, tokens);
        log.info("[stats-service] increment ok id={}", id);
        return id;
    }
}
