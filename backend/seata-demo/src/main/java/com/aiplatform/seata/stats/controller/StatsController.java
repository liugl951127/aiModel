package com.aiplatform.seata.stats.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.seata.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/seata/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @PostMapping("/increment")
    public Result<Long> increment(@RequestBody Map<String, Object> body) {
        String agentCode = (String) body.get("agentCode");
        Long tokens = ((Number) body.get("tokens")).longValue();
        return Result.success(statsService.increment(agentCode, tokens));
    }
}
