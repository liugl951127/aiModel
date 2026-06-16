package com.aiplatform.workflow.service;

import com.aiplatform.workflow.model.WorkflowSpec;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流定义仓库 (内存版, 可后续替换为 DB).
 *
 * <p>前台 "工作流管理" 页用 — 让用户保存/编辑/复用编排好的工作流,
 * 不需要每次重新拖节点。生命周期与 WorkflowRun (执行实例) 区分开。</p>
 */
@Service
public class WorkflowSpecRepository {

    private final Map<String, WorkflowSpec> specs = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> createTimes = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> updateTimes = new ConcurrentHashMap<>();
    private final Map<String, String> authors = new ConcurrentHashMap<>();   // username
    private final Map<String, Integer> runCounts = new ConcurrentHashMap<>();

    public WorkflowSpec save(WorkflowSpec spec) {
        if (spec.getId() == null || spec.getId().isBlank()) {
            spec.setId("wfs-" + UUID.randomUUID().toString().substring(0, 8));
            createTimes.put(spec.getId(), LocalDateTime.now());
        } else {
            createTimes.putIfAbsent(spec.getId(), LocalDateTime.now());
        }
        updateTimes.put(spec.getId(), LocalDateTime.now());
        specs.put(spec.getId(), spec);
        return spec;
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WorkflowSpec s : specs.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("description", s.getDescription());
            m.put("stepCount", s.getSteps() == null ? 0 : s.getSteps().size());
            m.put("createTime", createTimes.get(s.getId()));
            m.put("updateTime", updateTimes.get(s.getId()));
            m.put("author", authors.get(s.getId()));
            m.put("runCount", runCounts.getOrDefault(s.getId(), 0));
            out.add(m);
        }
        out.sort((a, b) -> ((LocalDateTime) b.get("updateTime")).compareTo((LocalDateTime) a.get("updateTime")));
        return out;
    }

    public WorkflowSpec get(String id) {
        return specs.get(id);
    }

    public void delete(String id) {
        specs.remove(id);
        createTimes.remove(id);
        updateTimes.remove(id);
        authors.remove(id);
        runCounts.remove(id);
    }

    public void setAuthor(String id, String author) {
        authors.put(id, author);
    }

    public void incrementRunCount(String id) {
        runCounts.merge(id, 1, Integer::sum);
    }

    public int size() {
        return specs.size();
    }
}
