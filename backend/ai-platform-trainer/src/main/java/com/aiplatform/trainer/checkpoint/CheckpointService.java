package com.aiplatform.trainer.checkpoint;

import com.aiplatform.trainer.model.MiniTransformer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 训练 checkpoint 服务。
 *
 * <p>负责把 {@link MiniTransformer} 的全部参数以 JSON 形式持久化到本地
 * 目录（生产可换 S3）。同时维护"只保留最近 N 个 checkpoint"的滚动策略，
 * 防止磁盘爆掉。</p>
 *
 * <h2>checkpoint 格式</h2>
 * <pre>
 *   ckpt-001/
 *     model.json        params (Base64-encoded float32 tensors)
 *     config.json       iter / loss / lr / timestamp
 *     README.md
 * </pre>
 */
@Slf4j
@Service
public class CheckpointService {

    @Value("${aiplatform.trainer.checkpoint-root:/opt/ai-platform/checkpoints}")
    private String root;

    @Value("${aiplatform.trainer.keep-last:3}")
    private int keepLast;

    /**
     * 把模型写入 checkpoint，返回 checkpoint 目录路径。
     *
     * @param model   训练中的模型
     * @param iter    当前步
     * @param loss    当前 loss
     * @param lr      当前学习率
     * @param extra   自定义元数据
     * @return checkpoint 目录
     */
    public Path save(MiniTransformer model, int iter, double loss, double lr, String extra) throws IOException {
        String name = "ckpt-" + String.format("%05d", iter);
        Path dir = Paths.get(root, name);
        Files.createDirectories(dir);
        writeParams(dir, model);
        writeMeta(dir, new Meta(iter, loss, lr, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), extra));
        Files.writeString(dir.resolve("README.md"),
                "# Checkpoint " + name + "\nloss=" + loss + " lr=" + lr + "\n", StandardCharsets.UTF_8);
        log.info("[CKPT] saved {} (loss={}, lr={})", name, loss, lr);
        rotate();
        return dir;
    }

    /**
     * 列出所有 checkpoint（最新在前）。
     */
    public List<Path> list() throws IOException {
        Path r = Paths.get(root);
        if (!Files.exists(r)) return List.of();
        try (Stream<Path> s = Files.list(r)) {
            return s.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("ckpt-"))
                    .sorted(Comparator.<Path>comparingInt(p -> -ckptStep(p.getFileName().toString())))
                    .toList();
        }
    }

    /**
     * 删除除最近 N 个之外的所有 checkpoint。
     */
    public void rotate() throws IOException {
        List<Path> all = list();
        if (all.size() <= keepLast) return;
        for (Path old : all.subList(keepLast, all.size())) {
            deleteRecursively(old);
            log.info("[CKPT] rotated out {}", old.getFileName());
        }
    }

    /**
     * 完全清理（包括保留的）。
     */
    public void clear() throws IOException {
        for (Path p : list()) deleteRecursively(p);
    }

    /* helpers */
    private static void writeParams(Path dir, MiniTransformer model) throws IOException {
        com.alibaba.fastjson2.JSONObject w = new com.alibaba.fastjson2.JSONObject();
        encode(w, model.getWte(), "wte");
        encode(w, model.getWpe(), "wpe");
        encode(w, model.getLnFg(), "ln_f_g");
        encode(w, model.getLnFb(), "ln_f_b");
        for (MiniTransformer.Parameter p : model.getBlockParams()) encode(w, p.array(), p.name());
        Files.writeString(dir.resolve("model.json"), w.toJSONString());
    }

    private static void writeMeta(Path dir, Meta m) throws IOException {
        com.alibaba.fastjson2.JSONObject c = new com.alibaba.fastjson2.JSONObject();
        c.put("iter", m.iter);
        c.put("loss", m.loss);
        c.put("lr", m.lr);
        c.put("ts", m.ts);
        c.put("extra", m.extra);
        Files.writeString(dir.resolve("config.json"), c.toJSONString());
    }

    private static void encode(com.alibaba.fastjson2.JSONObject out, ai.djl.ndarray.NDArray arr, String name) {
        float[] data = arr.toFloatArray();
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(data.length * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bb.asFloatBuffer().put(data);
        out.put(name, java.util.Base64.getEncoder().encodeToString(bb.array()));
    }

    private static int ckptStep(String name) {
        try { return Integer.parseInt(name.replace("ckpt-", "")); }
        catch (NumberFormatException e) { return -1; }
    }

    private static void deleteRecursively(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (var s = Files.walk(p)) {
            s.sorted(Comparator.reverseOrder()).forEach(child -> {
                try { Files.delete(child); } catch (IOException ignored) {}
            });
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Meta {
        private int iter;
        private double loss;
        private double lr;
        private String ts;
        private String extra;
    }
}
