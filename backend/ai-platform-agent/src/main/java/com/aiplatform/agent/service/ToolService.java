package com.aiplatform.agent.service;

import com.aiplatform.agent.entity.ToolEntity;
import com.aiplatform.agent.mapper.ToolMapper;
import com.aiplatform.starter.mybatis.entity.PageQuery;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.starter.mybatis.support.PageAdapter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ToolService {

    private final ToolMapper toolMapper;

    public ToolEntity create(ToolEntity tool) {
        if (tool.getToolCode() == null) {
            tool.setToolCode("T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (tool.getStatus() == null) tool.setStatus(1);
        toolMapper.insert(tool);
        return tool;
    }

    public List<ToolEntity> list() {
        return toolMapper.selectList(new LambdaQueryWrapper<ToolEntity>()
                .orderByDesc(ToolEntity::getCreateTime));
    }

    public PageResult<ToolEntity> page(PageQuery q) {
        Page<ToolEntity> p = q.toPage();
        LambdaQueryWrapper<ToolEntity> w = new LambdaQueryWrapper<>();
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            w.like(ToolEntity::getToolName, q.getKeyword());
        }
        w.orderByDesc(ToolEntity::getCreateTime);
        return PageAdapter.of(toolMapper.selectPage(p, w));
    }

    public void delete(Long id) {
        toolMapper.deleteById(id);
    }
}
