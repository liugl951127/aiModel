package com.aiplatform.model.service;

import com.aiplatform.starter.mybatis.entity.PageQuery;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.starter.mybatis.support.PageAdapter;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.model.entity.ModelRegistry;
import com.aiplatform.model.mapper.ModelRegistryMapper;
import com.aiplatform.redis.distributed.DistributedCache;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * ★ v3.x 性能优化: 高频读 query 加 Redis 分布式缓存.
 * <ul>
 *   <li>list() - 模型列表 (Dashboard/Models/Inference 等 5+ 页面调用)</li>
 *   <li>getById() - 单个模型详情</li>
 *   <li>page() - 分页列表</li>
 * </ul>
 * 写操作 (register/update/activate) 主动清缓存.
 *
 * <p>缓存 key 用 namespace + id 避免冲突, ttl 60s (模型列表不常变).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRegistryService {

    /** 模型列表缓存 key + ttl */
    private static final String CACHE_KEY_LIST = "aiplatform:model:list";
    private static final String CACHE_KEY_PREFIX = "aiplatform:model:";
    private static final int CACHE_TTL_SEC = 60;

    private final ModelRegistryMapper modelMapper;
    private final DistributedCache cache;
    private final ObjectMapper objectMapper;

    public ModelRegistry register(ModelRegistry model) {
        if (model.getModelCode() == null) {
            model.setModelCode("M-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (model.getStatus() == null) {
            model.setStatus("draft");
        }
        if (model.getModelCode() == null || model.getModelCode().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "模型编码必填");
        }
        if (modelMapper.selectCount(new LambdaQueryWrapper<ModelRegistry>()
                .eq(ModelRegistry::getModelCode, model.getModelCode())) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "模型编码已存在: " + model.getModelCode());
        }
        modelMapper.insert(model);
        invalidateCache();
        return model;
    }

    public ModelRegistry getById(Long id) {
        String key = CACHE_KEY_PREFIX + id;
        return cache.getOrLoad(key, CACHE_TTL_SEC, () -> modelMapper.selectById(id), ModelRegistry.class);
    }

    public List<ModelRegistry> list() {
        String key = CACHE_KEY_LIST;
        return cache.getOrLoadJson(key, CACHE_TTL_SEC,
            () -> modelMapper.selectList(new LambdaQueryWrapper<ModelRegistry>()
                .orderByDesc(ModelRegistry::getCreateTime)),
            new TypeReference<List<ModelRegistry>>() {});
    }

    public PageResult<ModelRegistry> page(PageQuery q) {
        // 分页不缓存 (分页参数变化多, 缓存命中率低)
        Page<ModelRegistry> page = q.toPage();
        LambdaQueryWrapper<ModelRegistry> w = new LambdaQueryWrapper<>();
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            w.like(ModelRegistry::getModelName, q.getKeyword())
             .or().like(ModelRegistry::getModelCode, q.getKeyword());
        }
        w.orderByDesc(ModelRegistry::getCreateTime);
        return PageAdapter.of(modelMapper.selectPage(page, w));
    }

    public ModelRegistry update(ModelRegistry model) {
        if (model.getId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "ID 必填");
        }
        modelMapper.updateById(model);
        invalidateCache(model.getId());
        return model;
    }

    public void delete(Long id) {
        modelMapper.deleteById(id);
        invalidateCache(id);
    }

    public List<ModelRegistry> listVersions(String modelCode) {
        String key = CACHE_KEY_PREFIX + "code:" + modelCode;
        return cache.getOrLoadJson(key, CACHE_TTL_SEC,
            () -> modelMapper.selectList(new LambdaQueryWrapper<ModelRegistry>()
                .eq(ModelRegistry::getModelCode, modelCode)
                .orderByDesc(ModelRegistry::getCreateTime)),
            new TypeReference<List<ModelRegistry>>() {});
    }

    public ModelRegistry activate(Long id) {
        ModelRegistry model = modelMapper.selectById(id);
        if (model == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "模型不存在");
        }
        model.setStatus("active");
        modelMapper.updateById(model);
        invalidateCache(id);
        return model;
    }

    private void invalidateCache() {
        cache.invalidate(CACHE_KEY_LIST);
    }

    private void invalidateCache(Long id) {
        cache.invalidate(CACHE_KEY_PREFIX + id);
        cache.invalidate(CACHE_KEY_LIST);
    }
}