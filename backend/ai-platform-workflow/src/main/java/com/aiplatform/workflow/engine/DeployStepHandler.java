package com.aiplatform.workflow.engine;

import com.aiplatform.workflow.model.WorkflowSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Registers a trained bundle with the {@code ai-platform-inference}
 * service so it becomes routable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeployStepHandler implements StepHandler {

    @Value("${aiplatform.inference.base-url:http://127.0.0.1:9008}")
    private String baseUrl;

    private final RestClient http = RestClient.builder().build();

    @Override public String type() { return "deploy"; }

    @Override
    public void execute(WorkflowSpec.Step step, Map<String, Object> context, StepLog slog) {
        String bundle = (String) context.get("bundleName");
        if (bundle == null) throw new IllegalStateException("no bundleName in context");
        slog.info("deploying bundle " + bundle + " to " + baseUrl);
        // The inference service auto-discovers bundles on its bundle-root
        // directory; we just have to make sure the trainer's export-root
        // matches the inference service's bundle-root.  We still POST a
        // refresh signal in case the registry supports it.
        try {
            http.post().uri(baseUrl + "/api/inference/reload")
                    .body(Map.of("bundleName", bundle))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            slog.info("inference reload endpoint not available, relying on disk auto-discovery: " + e.getMessage());
        }
        context.put("deployed", true);
    }
}
