package com.aiplatform.seata.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户 AI 额度表。{@code credits} 是用户在 AI Agent Platform 上可用的 token 数。
 *
 * <p>每次 ReAct 任务结束会扣减（{@code consumed += tokens}），余额不足时抛业务异常
 * 触发 seata 全局回滚。</p>
 */
@Data
@TableName("user_credits")
public class UserCredits {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String username;
    private Long credits;          // 剩余可用
    private Long consumed;         // 累计已消耗
    private LocalDateTime updateTime;
}
