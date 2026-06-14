package com.aiplatform.agent.feign;

import com.aiplatform.common.result.Result;
import com.aiplatform.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class InferenceServiceFallback implements FallbackFactory<InferenceServiceClient> {
    @Override
    public InferenceServiceClient create(Throwable cause) {
        log.error("[FEIGN] inference fallback: {}", cause.getMessage());
        return body -> Result.fail(ResultCode.FAIL.getCode(), "推理服务不可用: " + cause.getMessage());
    }
}
