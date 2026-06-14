package com.aiplatform.agent.controller;

import com.aiplatform.agent.entity.MultiAgentCase;
import com.aiplatform.agent.service.MultiAgentCaseService;
import com.aiplatform.common.annotation.IgnoreTenant;
import com.aiplatform.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 多智能体案例展示端点。
 * <p>前端项目成果展示页直接对接这些接口，按业务域/推荐位渲染案例。</p>
 *
 * <ul>
 *   <li>{@code GET /api/agent/cases/list?domain=marketing} — 列出案例</li>
 *   <li>{@code GET /api/agent/cases/{caseKey}}            — 单个案例详情</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/cases")
@IgnoreTenant
@RequiredArgsConstructor
public class MultiAgentCaseController {

    private final MultiAgentCaseService service;

    /**
     * 列出所有案例，可按 domain 过滤；featured 在前。
     */
    @GetMapping("/list")
    public Result<List<MultiAgentCase>> list(
            @RequestParam(value = "domain", required = false) String domain) {
        return Result.success(service.listByDomain(domain));
    }

    /**
     * 按 caseKey 获取单个案例（包含 agentSpec / flowSpec / finalOutput / kpis 全部 JSON）。
     */
    @GetMapping("/{caseKey}")
    public Result<MultiAgentCase> get(@PathVariable("caseKey") String caseKey) {
        MultiAgentCase c = service.getByKey(caseKey);
        if (c == null) {
            return Result.fail(404, "case not found: " + caseKey);
        }
        return Result.success(c);
    }
}
