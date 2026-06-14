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
}
