package com.aiplatform.workflow.mapper;

import com.aiplatform.workflow.entity.WorkflowRunEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 工作流运行 MyBatis-Plus Mapper.
 */
@Mapper
public interface WorkflowRunMapper extends BaseMapper<WorkflowRunEntity> {

    /** 状态变更 (SSE 推送时调). */
    @Update("UPDATE workflow_run SET status = #{status}, progress = #{progress}, current_step = #{currentStep}, update_time = NOW() WHERE run_id = #{runId} AND deleted = 0")
    int updateProgress(@Param("runId") String runId,
                       @Param("status") String status,
                       @Param("progress") int progress,
                       @Param("currentStep") String currentStep);

    /** 完成时一次性更新 (成功/失败都走这). */
    @Update("UPDATE workflow_run SET status = #{status}, progress = #{progress}, " +
            "output = #{output}, failed_node_id = #{failedNodeId}, failed_node_name = #{failedNodeName}, " +
            "failed_reason = #{failedReason}, duration_ms = #{durationMs}, " +
            "started_at = #{startedAt}, finished_at = #{finishedAt}, update_time = NOW() " +
            "WHERE run_id = #{runId} AND deleted = 0")
    int updateFinish(@Param("runId") String runId,
                     @Param("status") String status,
                     @Param("progress") int progress,
                     @Param("output") String output,
                     @Param("failedNodeId") String failedNodeId,
                     @Param("failedNodeName") String failedNodeName,
                     @Param("failedReason") String failedReason,
                     @Param("durationMs") Long durationMs,
                     @Param("startedAt") java.time.LocalDateTime startedAt,
                     @Param("finishedAt") java.time.LocalDateTime finishedAt);
}