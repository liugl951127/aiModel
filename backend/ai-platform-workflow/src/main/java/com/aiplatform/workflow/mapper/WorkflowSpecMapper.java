package com.aiplatform.workflow.mapper;

import com.aiplatform.workflow.entity.WorkflowSpecEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 工作流定义 MyBatis-Plus Mapper.
 */
@Mapper
public interface WorkflowSpecMapper extends BaseMapper<WorkflowSpecEntity> {

    /** 原子递增 runCount (执行一次 +1). */
    @Update("UPDATE workflow_spec SET run_count = IFNULL(run_count,0) + 1, last_run_at = NOW() WHERE id = #{id} AND deleted = 0")
    int incrRunCount(@Param("id") Long id);
}