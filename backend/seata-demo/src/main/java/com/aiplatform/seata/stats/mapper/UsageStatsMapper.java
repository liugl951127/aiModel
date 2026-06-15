package com.aiplatform.seata.stats.mapper;

import com.aiplatform.seata.stats.entity.UsageStats;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

@Mapper
public interface UsageStatsMapper extends BaseMapper<UsageStats> {

    @Select("SELECT id FROM usage_stats WHERE stat_date = #{date} AND agent_code = #{agentCode} LIMIT 1")
    Long findId(@Param("date") String date, @Param("agentCode") String agentCode);

    @Update("UPDATE usage_stats SET invoke_count = invoke_count + 1, token_total = token_total + #{tokens} " +
            "WHERE id = #{id}")
    int increment(@Param("id") Long id, @Param("tokens") Long tokens);
}
