package com.aiplatform.seata.user.service;

import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.seata.user.entity.UserCredits;
import com.aiplatform.seata.user.mapper.UserCreditsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 用户额度服务。
 *
 * <p>本类的方法被 seata 协调时，会自动注册到全局事务（{@code @GlobalTransactional}）。
 * 单独调用也合法（非分布式场景）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserCreditsMapper mapper;

    public UserCredits get(Long userId) {
        return mapper.selectById(userId);
    }

    /**
     * 扣减用户 AI 额度。
     *
     * @param userId  用户 ID
     * @param tokens  消耗 token 数
     * @return 扣减后余额
     * @throws BusinessException 余额不足时抛 {@code USER_CREDITS_NOT_ENOUGH}，
     *                           seata coordinator 会把所有分支事务回滚
     */
    public Long deduct(Long userId, Long tokens) {
        log.info("[user-service] deduct userId={} tokens={}", userId, tokens);
        int rows = mapper.deduct(userId, tokens);
        if (rows == 0) {
            throw new BusinessException(ResultCode.FAIL,
                    "user_credits_not_enough: userId=" + userId + ", need=" + tokens);
        }
        UserCredits after = mapper.selectById(userId);
        log.info("[user-service] deduct ok, remaining credits={}", after.getCredits());
        return after.getCredits();
    }
}
