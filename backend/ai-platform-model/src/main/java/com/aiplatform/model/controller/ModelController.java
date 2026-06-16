package com.aiplatform.model.controller;

import com.aiplatform.starter.mybatis.entity.PageQuery;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.common.result.Result;
import com.aiplatform.model.entity.ModelRegistry;
import com.aiplatform.model.service.ModelRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/model")
@RequiredArgsConstructor
public class ModelController {

    private final ModelRegistryService modelService;

    @PostMapping
    public Result<ModelRegistry> create(@RequestBody ModelRegistry model) {
        return Result.success(modelService.register(model));
    }

    @GetMapping("/list")
    public Result<List<ModelRegistry>> list() {
        return Result.success(modelService.list());
    }

    @GetMapping("/page")
    public PageResult<ModelRegistry> page(PageQuery q) {
        return modelService.page(q);
    }

    @GetMapping("/{id}")
    public Result<ModelRegistry> get(@PathVariable Long id) {
        return Result.success(modelService.getById(id));
    }

    @PutMapping
    public Result<ModelRegistry> update(@RequestBody ModelRegistry model) {
        return Result.success(modelService.update(model));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        modelService.delete(id);
        return Result.success();
    }

    /** 同一 modelCode 下所有版本 */
    @GetMapping("/versions/{modelCode}")
    public Result<List<ModelRegistry>> listVersions(@PathVariable String modelCode) {
        return Result.success(modelService.listVersions(modelCode));
    }

    /** 激活指定版本 (其它同 modelCode 归档) */
    @PostMapping("/{id}/activate")
    public Result<ModelRegistry> activate(@PathVariable Long id) {
        return Result.success(modelService.activate(id));
    }

    /** 比较两个版本 */
    @GetMapping("/compare")
    public Result<Map<String, Object>> compare(@RequestParam Long a, @RequestParam Long b) {
        return Result.success(modelService.compare(a, b));
    }

    /** 统计 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(modelService.stats());
    }

    /** 新增版本 (沿用 modelCode) */
    @PostMapping("/{modelCode}/new-version")
    public Result<ModelRegistry> newVersion(@PathVariable String modelCode, @RequestBody ModelRegistry model) {
        model.setModelCode(modelCode);
        if (model.getVersion() == null) model.setVersion("v0.1.0");
        if (model.getStatus() == null) model.setStatus("draft");
        return Result.success(modelService.register(model));
    }

    /**
     * 注册模型 (工作流编排节点). 接收 Map 简化参数.
     */
    @PostMapping("/register")
    public Result<Map<String, Object>> registerFromMap(@RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "unnamed-model");
        String stage = (String) body.getOrDefault("stage", "staging");
        Map<String, Object> ret = new java.util.HashMap<>();
        ret.put("id", System.currentTimeMillis());
        ret.put("name", name);
        ret.put("stage", stage);
        ret.put("version", "v0.1.0");
        ret.put("status", "registered");
        log.info("[MODEL] register name={}, stage={}", name, stage);
        return Result.success(ret);
    }

    /**
     * 部署模型 (工作流编排节点). K8s/灰度发布, 演示实现.
     */
    @PostMapping("/deploy")
    public Result<Map<String, Object>> deploy(@RequestBody Map<String, Object> body) {
        Object modelId = body.get("modelId");
        int replicas = body.get("replicas") == null ? 1 : ((Number) body.get("replicas")).intValue();
        int canary = body.get("canary") == null ? 0 : ((Number) body.get("canary")).intValue();
        Map<String, Object> ret = new java.util.HashMap<>();
        ret.put("modelId", modelId);
        ret.put("replicas", replicas);
        ret.put("canary", canary);
        ret.put("status", "deploying");
        ret.put("endpoint", "http://inference.ai-platform.svc/" + modelId);
        log.info("[MODEL] deploy modelId={}, replicas={}, canary={}", modelId, replicas, canary);
        return Result.success(ret);
    }
}
