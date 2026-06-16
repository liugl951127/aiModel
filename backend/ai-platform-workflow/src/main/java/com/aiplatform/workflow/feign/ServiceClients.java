package com.aiplatform.workflow.feign;

import com.aiplatform.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 集中式 feign 客户端: workflow 服务通过这些 client 调用其它服务.
 * 命名空间 service.feign.* 统一管理, 避免每个业务都写一套.
 */
public class ServiceClients {

    @FeignClient(name = "ai-platform-agent", path = "/api/agent")
    public interface AgentClient {
        @GetMapping("/list")
        Result<List<Map<String, Object>>> list();

        @PostMapping("/chat")
        Result<Map<String, Object>> chat(@RequestBody Map<String, Object> body);

        @PostMapping("/think")
        Result<Map<String, Object>> think(@RequestBody Map<String, Object> body);

        @PostMapping("/tool/invoke")
        Result<Map<String, Object>> invokeTool(@RequestBody Map<String, Object> body);
    }

    @FeignClient(name = "ai-platform-tool", path = "/api/tool")
    public interface ToolClient {
        @GetMapping("/list")
        Result<List<Map<String, Object>>> list();
    }

    @FeignClient(name = "ai-platform-agent", path = "/api/web")
    public interface WebSearchClient {
        @GetMapping("/search")
        Result<Map<String, Object>> search(@RequestParam("q") String q, @RequestParam(value = "max", defaultValue = "5") Integer max);
    }

    @FeignClient(name = "ai-platform-knowledge", path = "/api/knowledge")
    public interface KnowledgeClient {
        @GetMapping("/base/list")
        Result<List<Map<String, Object>>> listBases();

        @GetMapping("/document/page")
        Result<Map<String, Object>> docPage(@RequestParam Map<String, Object> params);

        @PostMapping("/search")
        Result<List<Map<String, Object>>> search(@RequestBody Map<String, Object> body);

        @PostMapping("/search-enhanced")
        Result<Map<String, Object>> enhancedSearch(@RequestBody Map<String, Object> body);

        @PostMapping("/embed")
        Result<Map<String, Object>> embed(@RequestBody Map<String, Object> body);

        @PostMapping("/vector/index")
        Result<Map<String, Object>> vectorIndex(@RequestBody Map<String, Object> body);

        @PostMapping("/chunk")
        Result<Map<String, Object>> chunk(@RequestBody Map<String, Object> body);
    }

    @FeignClient(name = "ai-platform-inference", path = "/api/inference")
    public interface InferenceClient {
        @GetMapping("/models")
        Result<List<Map<String, Object>>> models();

        @PostMapping("/generate")
        Result<Map<String, Object>> generate(@RequestBody Map<String, Object> body);
    }

    @FeignClient(name = "ai-platform-trainer", path = "/api/trainer")
    public interface TrainerClient {
        @GetMapping("/models")
        Result<List<Map<String, Object>>> models();

        @PostMapping("/submit")
        Result<Map<String, Object>> submit(@RequestBody Map<String, Object> body);

        @PostMapping("/lora")
        Result<Map<String, Object>> lora(@RequestBody Map<String, Object> body);

        @PostMapping("/dpo")
        Result<Map<String, Object>> dpo(@RequestBody Map<String, Object> body);
    }

    @FeignClient(name = "ai-platform-model", path = "/api/model")
    public interface ModelClient {
        @GetMapping("/list")
        Result<List<Map<String, Object>>> list();

        @GetMapping("/page")
        Result<Map<String, Object>> page(@RequestParam Map<String, Object> params);

        @PostMapping("/register")
        Result<Map<String, Object>> register(@RequestBody Map<String, Object> body);

        @PostMapping("/deploy")
        Result<Map<String, Object>> deploy(@RequestBody Map<String, Object> body);
    }

    @FeignClient(name = "ai-platform-dataset", path = "/api/dataset")
    public interface DatasetClient {
        @GetMapping("/list")
        Result<List<Map<String, Object>>> list();

        @GetMapping("/page")
        Result<Map<String, Object>> page(@RequestParam Map<String, Object> params);
    }
}
