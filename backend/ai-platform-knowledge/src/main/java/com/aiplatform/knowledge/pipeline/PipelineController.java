package com.aiplatform.knowledge.pipeline;

import com.aiplatform.common.result.Result;
import com.aiplatform.knowledge.pipeline.PipelineDag.Pipeline;
import com.aiplatform.knowledge.pipeline.PipelineService.RunResult;
import com.aiplatform.knowledge.pipeline.registry.NodeRegistry;
import com.aiplatform.knowledge.pipeline.registry.NodeRegistry.Summary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST surface for the pipeline orchestrator.
 *
 * <ul>
 *   <li>GET    /api/knowledge/pipeline/nodes     - list available node types</li>
 *   <li>POST   /api/knowledge/pipeline           - save a new pipeline</li>
 *   <li>GET    /api/knowledge/pipeline           - list saved pipelines</li>
 *   <li>GET    /api/knowledge/pipeline/{id}      - read a pipeline</li>
 *   <li>DELETE /api/knowledge/pipeline/{id}      - delete a pipeline</li>
 *   <li>POST   /api/knowledge/pipeline/{id}/run  - execute + return the full result</li>
 *   <li>GET    /api/knowledge/pipeline/run/{rid} - inspect a past run</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/knowledge/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService service;
    private final NodeRegistry nodeRegistry;

    @GetMapping("/nodes")
    public Result<List<Summary>> nodes() {
        return Result.success(nodeRegistry.summaries());
    }

    @PostMapping
    public Result<Pipeline> save(@RequestBody Pipeline p) {
        return Result.success(service.save(p));
    }

    @GetMapping
    public Result<List<Pipeline>> list() {
        return Result.success(service.list());
    }

    @GetMapping("/{id}")
    public Result<Pipeline> get(@PathVariable String id) {
        Pipeline p = service.get(id);
        if (p == null) return Result.fail(404, "pipeline not found");
        return Result.success(p);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable String id) {
        service.delete(id);
        return Result.success(true);
    }

    @PostMapping("/{id}/run")
    public Result<RunResult> run(@PathVariable String id,
                                 @RequestParam(value = "query", defaultValue = "") String query,
                                 @RequestBody(required = false) Map<String, Object> config) {
        return Result.success(service.run(id, query, config));
    }

    @GetMapping("/run/{rid}")
    public Result<RunResult> getRun(@PathVariable String rid) {
        RunResult r = service.getRun(rid);
        if (r == null) return Result.fail(404, "run not found");
        return Result.success(r);
    }
}
