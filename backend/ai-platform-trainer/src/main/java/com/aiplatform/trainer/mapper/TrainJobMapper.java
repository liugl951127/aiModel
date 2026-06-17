package com.aiplatform.trainer.mapper;

import com.aiplatform.trainer.entity.TrainJobEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 训练任务 MyBatis-Plus Mapper.
 */
@Mapper
public interface TrainJobMapper extends BaseMapper<TrainJobEntity> {

    /** 原子更新进度 + status (SSE 推送时高频调). */
    @Update("UPDATE model_train_job SET progress = #{progress}, status = #{status}, update_time = NOW() WHERE job_code = #{jobCode} AND deleted = 0")
    int updateProgress(@Param("jobCode") String jobCode, @Param("progress") int progress, @Param("status") String status);

    /** 完成时一次性更新所有字段 (finalLoss 存到 metrics JSON). */
    @Update("UPDATE model_train_job SET status = #{status}, progress = #{progress}, " +
            "output_path = #{outputPath}, error_message = #{errorMessage}, " +
            "metrics = #{metrics}, " +
            "started_at = #{startedAt}, finished_at = #{finishedAt}, update_time = NOW() " +
            "WHERE job_code = #{jobCode} AND deleted = 0")
    int updateFinish(@Param("jobCode") String jobCode,
                     @Param("status") String status,
                     @Param("progress") int progress,
                     @Param("outputPath") String outputPath,
                     @Param("errorMessage") String errorMessage,
                     @Param("metrics") String metrics,
                     @Param("startedAt") java.time.LocalDateTime startedAt,
                     @Param("finishedAt") java.time.LocalDateTime finishedAt);
}