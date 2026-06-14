package com.aiplatform.workflow.engine;

import com.aiplatform.workflow.model.WorkflowSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Triggers a training job on the {@code ai-platform-trainer} service and
 * blocks until the job reaches a terminal state.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrainStepHandler implements StepHandler {

    @Value("${aiplatform.trainer.base-url:http://127.0.0.1:9008}")
    private String baseUrl;

    private final RestClient http = RestClient.builder().build();

    @Override
    public String type() { return "train"; }

    @Override
    public void execute(WorkflowSpec.Step step, Map<String, Object> context, StepLog slog) throws Exception {
        String corpus = (String) step.getParams().get("corpusPath");
        if (corpus == null && context.containsKey("corpusPath")) {
            corpus = (String) context.get("corpusPath");
        }
        if (corpus == null) throw new IllegalArgumentException("train step missing corpusPath");

        @SuppressWarnings({"rawtypes", "unchecked"})
        Map<String, Object> body = new java.util.HashMap<>(step.getParams());
        body.put("corpusPath", corpus);

        slog.info("submitting training job to " + baseUrl);
        @SuppressWarnings("rawtypes")
        java.util.Map r = http.post().uri(baseUrl + "/api/trainer/submit")
                .body(body)
                .retrieve()
                .body(java.util.Map.class);
        if (r == null) throw new IllegalStateException("empty trainer response");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        String jobId = (String) data.get("jobId");
        slog.info("training jobId=" + jobId);
        context.put("trainJobId", jobId);

        // Poll until terminal
        long deadline = System.currentTimeMillis() + 6 * 60 * 60 * 1000L; // 6h ceiling
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(3000);
            @SuppressWarnings("rawtypes")
            java.util.Map jr = http.get().uri(baseUrl + "/api/trainer/job/" + jobId)
                    .retrieve().body(java.util.Map.class);
            if (jr == null) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> jd = (Map<String, Object>) jr.get("data");
            String status = (String) jd.get("status");
            slog.info("job " + jobId + " status=" + status);
            if ("succeeded".equals(status)) {
                context.put("bundleName", jd.get("bundleName"));
                context.put("outputPath", jd.get("outputPath"));
                context.put("finalLoss", jd.get("finalLoss"));
                return;
            }
            if ("failed".equals(status)) {
                throw new RuntimeException("train job failed: " + jd.get("error"));
            }
        }
        throw new RuntimeException("train job timed out after 6h");
    }
}
