package com.aiplatform.model.service;

import com.aiplatform.starter.mybatis.entity.PageQuery;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.starter.mybatis.support.PageAdapter;
import com.aiplatform.model.entity.ModelDataset;
import com.aiplatform.model.mapper.ModelDatasetMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DatasetService {

    private final ModelDatasetMapper datasetMapper;

    public ModelDataset create(ModelDataset dataset) {
        if (dataset.getDatasetCode() == null) {
            dataset.setDatasetCode("D-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (dataset.getStatus() == null) {
            dataset.setStatus("active");
        }
        datasetMapper.insert(dataset);
        return dataset;
    }

    public PageResult<ModelDataset> page(PageQuery q) {
        Page<ModelDataset> page = q.toPage();
        LambdaQueryWrapper<ModelDataset> w = new LambdaQueryWrapper<>();
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            w.like(ModelDataset::getDatasetName, q.getKeyword());
        }
        w.orderByDesc(ModelDataset::getCreateTime);
        return PageAdapter.of(datasetMapper.selectPage(page, w));
    }

    public void delete(Long id) {
        datasetMapper.deleteById(id);
    }
}
