package com.aiplatform.workflow.service;

import com.aiplatform.redis.distributed.DistributedCache;
import com.aiplatform.workflow.entity.WorkflowSpecEntity;
import com.aiplatform.workflow.mapper.WorkflowSpecMapper;
import com.aiplatform.workflow.model.WorkflowSpec;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作流定义仓库 — DB 版 (替代 ConcurrentHashMap).
 *
 * <p>前端发的是 {name, nodes:[{id,type,name,x,y,params}], edges:[{from,to,fromPort,toPort}]}.
 * 后端以前用 WorkflowSpec (id,name,description,steps) 反序列化会失败 (字段对不上).
 * 现在用 JsonNode / Map 接住, 原样存, 不强制 schema.</p>
 *
 * <p>★ v3.x 性能优化: list/getById 加缓存 (工作流管理页高频调用).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowSpecRepository {

    private static final String CACHE_KEY_LIST = "aiplatform:wf:spec:list";
    private static final String CACHE_KEY_LIST_SIMPLE = "aiplatform:wf:spec:list-simple";
    private static final String CACHE_KEY_PREFIX = "aiplatform:wf:spec:";
    private static final int CACHE_TTL_SEC = 300;

    private final WorkflowSpecMapper mapper;
    private final DistributedCache cache;

    /** 保存 (新建 or 更新). */
    public WorkflowSpecEntity save(String name, String author, String description,
                                   String specJson, int nodeCount, int edgeCount) {
        WorkflowSpecEntity e = new WorkflowSpecEntity();
        e.setName(name == null ? "(未命名)" : name);
        e.setAuthor(author == null ? "anonymous" : author);
        e.setDescription(description);
        e.setSpecJson(specJson == null ? "{}" : specJson);
        e.setNodeCount(nodeCount);
        e.setEdgeCount(edgeCount);
        e.setRunCount(0);
        mapper.insert(e);
        log.info("[WF-DB] saved id={} name={} author={} nodes={} edges={}",
                e.getId(), e.getName(), e.getAuthor(), nodeCount, edgeCount);
        invalidateListCache();
        return e;
    }

    /** 查列表 (按更新时间倒序). */
    public List<WorkflowSpecEntity> list() {
        return cache.getOrLoadJson(CACHE_KEY_LIST, CACHE_TTL_SEC,
            () -> mapper.selectList(
                new QueryWrapper<WorkflowSpecEntity>()
                    .eq("deleted", 0)
                    .orderByDesc("update_time")
                    .last("LIMIT 500")
            ),
            new TypeReference<List<WorkflowSpecEntity>>() {});
    }

    /** 按 id 查. */
    public WorkflowSpecEntity getById(Long id) {
        return cache.getOrLoad(CACHE_KEY_PREFIX + id, CACHE_TTL_SEC,
            () -> mapper.selectById(id), WorkflowSpecEntity.class);
    }

    /** 删除 (软删). */
    public void delete(Long id) {
        mapper.deleteById(id);
        invalidateListCache();
        cache.invalidate(CACHE_KEY_PREFIX + id);
    }

    /** 复制. */
    public WorkflowSpecEntity duplicate(Long srcId, String newAuthor) {
        WorkflowSpecEntity src = getById(srcId);
        if (src == null) return null;
        WorkflowSpecEntity copy = new WorkflowSpecEntity();
        copy.setName(src.getName() + " (副本)");
        copy.setAuthor(newAuthor != null ? newAuthor : src.getAuthor());
        copy.setDescription(src.getDescription());
        copy.setSpecJson(src.getSpecJson());
        copy.setNodeCount(src.getNodeCount());
        copy.setEdgeCount(src.getEdgeCount());
        copy.setRunCount(0);
        mapper.insert(copy);
        invalidateListCache();
        return copy;
    }

    /** 原子递增 runCount. */
    public void incrRunCount(Long id) {
        mapper.incrRunCount(id);
        // 清缓存 (状态变化)
        cache.invalidate(CACHE_KEY_PREFIX + id);
        invalidateListCache();
    }

    /**
     * 兼容老 WorkflowSpec 模型: 给 WorkflowEngine.runAsync 用的.
     * 老 WorkflowSpec 期望 {id,name,steps:[{type,name,dependsOn,params}]}.
     * 这里把前端 specJson 转成 WorkflowSpec (steps 从 nodes+edges 推 dependsOn).
     */
    public WorkflowSpec toLegacySpec(WorkflowSpecEntity e) {
        if (e == null) return null;
        WorkflowSpec spec = new WorkflowSpec();
        spec.setId(String.valueOf(e.getId()));
        spec.setName(e.getName());
        spec.setDescription(e.getDescription());

        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(e.getSpecJson());
            List<WorkflowSpec.Step> steps = new ArrayList<>();
            Map<String, List<String>> deps = new HashMap<>();
            if (root.has("edges")) {
                for (com.fasterxml.jackson.databind.JsonNode edge : root.get("edges")) {
                    String from = edge.path("from").asText();
                    String to = edge.path("to").asText();
                    if (from == null || to == null || from.isEmpty() || to.isEmpty()) continue;
                    deps.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
                }
            }
            if (root.has("nodes")) {
                for (com.fasterxml.jackson.databind.JsonNode node : root.get("nodes")) {
                    WorkflowSpec.Step s = new WorkflowSpec.Step();
                    s.setType(node.path("type").asText());
                    s.setName(node.path("name").asText(node.path("id").asText()));
                    s.setDependsOn(deps.getOrDefault(node.path("id").asText(), Collections.emptyList()));
                    Map<String, Object> params = new HashMap<>();
                    com.fasterxml.jackson.databind.JsonNode p = node.path("params");
                    if (p.isObject()) {
                        p.fields().forEachRemaining(ent -> params.put(ent.getKey(), ent.getValue()));
                    }
                    s.setParams(params);
                    steps.add(s);
                }
            }
            spec.setSteps(steps);
        } catch (Exception ex) {
            log.warn("[WF-DB] 解析 specJson 失败, 步骤列表空: {}", ex.getMessage());
            spec.setSteps(Collections.emptyList());
        }
        return spec;
    }

    /** 给前端 list 接口用 — 转成简易 Map (id, name, author, nodeCount, edgeCount, runCount, updateTime). */
    public List<Map<String, Object>> listSimple() {
        return cache.getOrLoadJson(CACHE_KEY_LIST_SIMPLE, CACHE_TTL_SEC,
            () -> list().stream().map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", e.getId());
                m.put("name", e.getName());
                m.put("author", e.getAuthor());
                m.put("description", e.getDescription());
                m.put("nodeCount", e.getNodeCount());
                m.put("edgeCount", e.getEdgeCount());
                m.put("runCount", e.getRunCount());
                m.put("lastRunAt", e.getLastRunAt());
                m.put("createTime", e.getCreateTime());
                m.put("updateTime", e.getUpdateTime());
                return m;
            }).collect(Collectors.toList()),
            new TypeReference<List<Map<String, Object>>>() {});
    }

    private void invalidateListCache() {
        cache.invalidate(CACHE_KEY_LIST);
        cache.invalidate(CACHE_KEY_LIST_SIMPLE);
    }
}