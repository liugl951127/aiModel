package com.aiplatform.seata.stats.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 调用统计（每日一行），给 Dashboard / 报表用。
 * 数据库 schema：{@code stats_stats}.
 */
@Data
@TableName("usage_stats")
public class UsageStats {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String statDate;          // yyyy-MM-dd
    private String agentCode;
    private Long invokeCount;
    private Long tokenTotal;
}
