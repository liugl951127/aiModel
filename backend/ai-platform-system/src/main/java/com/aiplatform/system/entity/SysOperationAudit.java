package com.aiplatform.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ★ P0-LEAD-1 操作审计实体 (处长要求: 谁在什么时间改了什么).
 */
@Data
@TableName("sys_operation_audit")
public class SysOperationAudit {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作用户 */
    private String username;
    private Long userId;
    private Long tenantId;

    /** 操作模块 (如 用户/角色/智能体) */
    private String module;
    /** 操作类型 (CREATE/UPDATE/DELETE/EXPORT/IMPORT/LOGIN 等) */
    private String operation;
    /** 操作描述 */
    private String description;
    /** 业务对象 id (被操作的对象) */
    private String bizId;
    /** 请求方法 (POST/PUT/DELETE) */
    private String httpMethod;
    /** 请求路径 */
    private String requestPath;
    /** 请求参数 (脱敏后) */
    private String requestParams;
    /** 响应码 */
    private Integer responseCode;
    /** 操作 IP */
    private String clientIp;
    /** 用户代理 */
    private String userAgent;
    /** 耗时 ms */
    private Long costMs;
    /** 状态 (SUCCESS/FAILED) */
    private String status;
    /** 错误信息 */
    private String errorMessage;

    private LocalDateTime createTime;
}
