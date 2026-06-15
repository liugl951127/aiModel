package com.aiplatform.seata.user.mapper;

import com.aiplatform.seata.user.entity.UserCredits;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserCreditsMapper extends BaseMapper<UserCredits> {

    /**
     * 扣减额度（带余额校验：credits >= tokens 才扣）。
     * 返回受影响行数 = 0 表示余额不足，触发 seata 全局回滚。
     */
    @Update("UPDATE user_credits SET credits = credits - #{tokens}, " +
            "consumed = consumed + #{tokens}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND credits >= #{tokens}")
    int deduct(@Param("userId") Long userId, @Param("tokens") Long tokens);
}
