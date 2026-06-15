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
     * 扣减用户 AI 额度。本地事务保护 — credits 不能被错误扣减。
     *
     * <p>有 seata TC 时上层 {@code @GlobalTransactional} 接管本事务；无 TC
     * 时本事务仍然是单 datasource 提交，不会被外部 rollback 影响。</p>
     */
    @org.springframework.transaction.annotation.Transactional
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
