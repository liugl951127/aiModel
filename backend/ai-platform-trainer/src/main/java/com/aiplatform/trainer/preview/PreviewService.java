package com.aiplatform.trainer.preview;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import com.aiplatform.trainer.constraints.ConstraintEngine;
import com.aiplatform.trainer.constraints.HallucinationGuardConfig;
import com.aiplatform.trainer.model.MiniTransformer;
import com.aiplatform.trainer.model.TransformerOps;
import com.aiplatform.trainer.rag.KnowledgeRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 实时预览服务。
 * <p>把生成 token 流式推送到连接的 {@link SseEmitter}，应用与训练后推理
 * 完全相同的 {@link HallucinationGuardConfig}，保证预览所见即所得。</p>
 *
 * <h2>事件类型</h2>
 * <ul>
 *   <li>{@code preview:token}    — 每个被接受的 token</li>
 *   <li>{@code preview:metric}   — loss / grad-norm / 准确率</li>
 *   <li>{@code preview:rejected} — 约束引擎拒绝某个 token</li>
 *   <li>{@code preview:done}     — 终止事件</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreviewService {

    private final ConstraintEngine engine;
    private final KnowledgeRetriever retriever;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "preview-worker");
        t.setDaemon(true);
        return t;
    });

    /** Active emitters keyed by jobId so we can clean up on stop. */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 订阅某个任务的预览事件流。
     * <p>返回的 {@link SseEmitter} 由 controller 直接交给前端。</p>
     */
    public SseEmitter subscribe(String jobId) {
        SseEmitter em = new SseEmitter(0L); // never timeout server-side
        emitters.put(jobId, em);
        em.onCompletion(() -> emitters.remove(jobId));
        em.onTimeout(() -> emitters.remove(jobId));
        em.onError(t -> emitters.remove(jobId));
        return em;
    }

    /**
     * 在当前模型状态下跑一次生成，流式返回事件给订阅的 emitter。
     *
     * @param jobId  任务 id
     * @param model  当前模型
     * @param prompt UTF-8 提示字节
     * @param guard  防幻觉配置
     * @param maxTokens 最大生成 token 数
     */
    public void runPreview(String jobId,
                           MiniTransformer model,
                           byte[] prompt,
                           HallucinationGuardConfig guard,
                           int maxTokens) {
        SseEmitter em = emitters.get(jobId);
        if (em == null) return;

        if (guard.isKnowledgeGrounding()) {
            // Pre-fetch grounding context for the prompt
            String q = new String(prompt, StandardCharsets.UTF_8);
            String ctx = retriever.retrieve(guard.getKnowledgeKbId(), q, guard.getKnowledgeTopK());
            engine.primeKBContext(ctx == null ? "" : ctx);
        }

        pool.submit(() -> {
            try (NDManager manager = NDManager.newBaseManager()) {
                int[] ids = toIds(prompt);
                int[] history = new int[0];
                StringBuilder acc = new StringBuilder();
                send(em, "preview:metric", Map.of(
                        "type", "start",
                        "maxTokens", maxTokens,
                        "guard", Map.of(
                                "topK", guard.getTopK(),
                                "topP", guard.getTopP(),
                                "temperature", guard.getTemperature(),
                                "repetitionPenalty", guard.getRepetitionPenalty(),
                                "knowledgeGrounding", guard.isKnowledgeGrounding(),
                                "minConfidence", guard.getMinConfidence(),
                                "factCheck", guard.isFactCheck()
                        )
                ));
                int consecUnknown = 0;
                for (int step = 0; step < maxTokens; step++) {
                    int T = Math.min(model.getBlockSize(), ids.length);
                    int[] ctx = new int[T];
                    System.arraycopy(ids, ids.length - T, ctx, 0, T);
                    NDArray idx = manager.create(
                            java.nio.ByteBuffer.wrap(toLongBytes(ctx)),
                            new ai.djl.ndarray.types.Shape(1, T),
                            ai.djl.ndarray.types.DataType.INT64);
                    NDArray logits = TransformerOps.forward(model, idx, manager);
                    NDArray last = logits.get("0," + (T - 1) + ",:");
                    ConstraintEngine.Decision d = engine.apply(last, history, guard);
                    if (!d.allowed) {
                        send(em, "preview:rejected", Map.of(
                                "step", step,
                                "reason", d.reason == null ? "unknown" : d.reason,
                                "sentinel", d.tokenId
                        ));
                        if (d.tokenId == ConstraintEngine.LOW_CONFIDENCE) {
                            send(em, "preview:done", Map.of("status", "low_confidence", "text", acc.toString()));
                            return;
                        }
                        consecUnknown++;
                        if (consecUnknown > guard.getMaxConsecutiveUnknowns()) {
                            send(em, "preview:done", Map.of("status", "aborted", "text", acc.toString()));
                            return;
                        }
                        continue;
                    }
                    consecUnknown = 0;
                    int tok = d.tokenId;
                    if (tok < 0 || tok >= 256) {
                        // safety: shouldn't reach here given the engine's OOD check
                        break;
                    }
                    char c = (char) tok;
                    acc.append(c);
                    history = append(history, tok);
                    ids = append(ids, tok);
                    send(em, "preview:token", Map.of(
                            "step", step,
                            "id", tok,
                            "char", String.valueOf(c),
                            "confidenceGap", d.confidenceGap
                    ));
                }
                send(em, "preview:done", Map.of("status", "ok", "text", acc.toString()));
            } catch (Throwable th) {
                log.error("[PREVIEW] failed: {}", th.getMessage(), th);
                try {
                    send(em, "preview:done", Map.of("status", "error", "error", th.getMessage()));
                } catch (Exception ignored) {
                }
            } finally {
                em.complete();
                emitters.remove(jobId);
            }
        });
    }

    /** 推送一个指标事件（训练循环调用）。 */
    public void publishMetric(String jobId, String name, Object value) {
        SseEmitter em = emitters.get(jobId);
        if (em == null) return;
        try {
            send(em, "preview:metric", Map.of("type", name, "value", value));
        } catch (Exception ignored) {
        }
    }

    /** 供本类与外部调用者使用的公共发送方法。 */
    public static void send(SseEmitter em, String name, Object data) {
        try {
            em.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /* helpers */
    private static int[] toIds(byte[] prompt) {
        int[] ids = new int[prompt.length];
        for (int i = 0; i < prompt.length; i++) ids[i] = prompt[i] & 0xff;
        return ids;
    }

    private static byte[] toLongBytes(int[] ids) {
        byte[] out = new byte[ids.length * 8];
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(out);
        for (int id : ids) bb.putLong(id);
        return out;
    }

    private static int[] append(int[] arr, int v) {
        int[] n = new int[arr.length + 1];
        System.arraycopy(arr, 0, n, 0, arr.length);
        n[arr.length] = v;
        return n;
    }
}
