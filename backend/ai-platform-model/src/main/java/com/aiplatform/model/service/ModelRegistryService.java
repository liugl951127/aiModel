package com.aiplatform.model.service;

import com.aiplatform.starter.mybatis.entity.PageQuery;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.starter.mybatis.support.PageAdapter;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.model.entity.ModelRegistry;
import com.aiplatform.model.mapper.ModelRegistryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModelRegistryService {

    private final ModelRegistryMapper modelMapper;

    public ModelRegistry register(ModelRegistry model) {
        if (model.getModelCode() == null) {
            model.setModelCode("M-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (model.getStatus() == null) {
            model.setStatus("draft");
        }
        if (model.getVersion() == null) {
            model.setVersion("v0.1.0");
        }
        modelMapper.insert(model);
        return model;
    }

    public ModelRegistry getById(Long id) {
        ModelRegistry m = modelMapper.selectById(id);
        if (m == null) throw new BusinessException(ResultCode.MODEL_NOT_FOUND);
        return m;
    }

    public List<ModelRegistry> list() {
        return modelMapper.selectList(new LambdaQueryWrapper<ModelRegistry>()
                .orderByDesc(ModelRegistry::getCreateTime));
    }

    public PageResult<ModelRegistry> page(PageQuery q) {
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
        return modelMapper.selectById(model.getId());
    }

    public void delete(Long id) {
        modelMapper.deleteById(id);
    }

    public void updateStatus(Long id, String status) {
        ModelRegistry m = new ModelRegistry();
        m.setId(id);
        m.setStatus(status);
        modelMapper.updateById(m);
    }

    public void recordExport(Long id, String onnxPath, String format) {
        ModelRegistry m = new ModelRegistry();
        m.setId(id);
        m.setOnnxPath(onnxPath);
        m.setExportFormat(format);
        m.setStatus("ready");
        modelMapper.updateById(m);
    }

    /** 同一 modelCode 下所有版本 (按创建时间倒序) */
    public List<ModelRegistry> listVersions(String modelCode) {
        return modelMapper.selectList(new LambdaQueryWrapper<ModelRegistry>()
                .eq(ModelRegistry::getModelCode, modelCode)
                .orderByDesc(ModelRegistry::getCreateTime));
    }

    /** 设为活跃版本: 同 modelCode 其它版本 archived */
    public ModelRegistry activate(Long id) {
        ModelRegistry m = getById(id);
        ModelRegistry upd = new ModelRegistry();
        upd.setId(id);
        upd.setStatus("active");
        modelMapper.updateById(upd);
        List<ModelRegistry> others = modelMapper.selectList(
                new LambdaQueryWrapper<ModelRegistry>()
                        .eq(ModelRegistry::getModelCode, m.getModelCode())
                        .ne(ModelRegistry::getId, id));
        for (ModelRegistry o : others) {
            ModelRegistry u = new ModelRegistry();
            u.setId(o.getId());
            u.setStatus("archived");
            modelMapper.updateById(u);
        }
        return getById(id);
    }

    /** 两个版本比较 */
    public java.util.Map<String, Object> compare(Long a, Long b) {
        ModelRegistry ma = getById(a);
        ModelRegistry mb = getById(b);
        java.util.Map<String, Object> diff = new java.util.LinkedHashMap<>();
        diff.put("a", ma);
        diff.put("b", mb);
        java.util.List<String> changes = new java.util.ArrayList<>();
        if (!java.util.Objects.equals(ma.getVersion(), mb.getVersion())) changes.add("version");
        if (!java.util.Objects.equals(ma.getStatus(), mb.getStatus())) changes.add("status");
        if (!java.util.Objects.equals(ma.getParameterCount(), mb.getParameterCount())) changes.add("parameterCount");
        if (!java.util.Objects.equals(ma.getContextLength(), mb.getContextLength())) changes.add("contextLength");
        if (!java.util.Objects.equals(ma.getOnnxPath(), mb.getOnnxPath())) changes.add("onnxPath");
        if (!java.util.Objects.equals(ma.getMetrics(), mb.getMetrics())) changes.add("metrics");
        diff.put("diff", changes);
        return diff;
    }

    /** 统计 */
    public java.util.Map<String, Object> stats() {
        Long total = modelMapper.selectCount(null);
        Long active = modelMapper.selectCount(new LambdaQueryWrapper<ModelRegistry>().eq(ModelRegistry::getStatus, "active"));
        Long draft = modelMapper.selectCount(new LambdaQueryWrapper<ModelRegistry>().eq(ModelRegistry::getStatus, "draft"));
        Long archived = modelMapper.selectCount(new LambdaQueryWrapper<ModelRegistry>().eq(ModelRegistry::getStatus, "archived"));
        return java.util.Map.of("total", total, "active", active, "draft", draft, "archived", archived);
    }
}
