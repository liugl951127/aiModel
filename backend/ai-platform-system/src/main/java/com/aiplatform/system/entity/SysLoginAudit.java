package com.aiplatform.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_login_audit")
public class SysLoginAudit {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String username;
    private Long tenantId;
    private Long userId;
    private String loginIp;
    private String userAgent;
    private String loginStatus;       // SUCCESS / FAILED / LOCKED
    private String failReason;
    private LocalDateTime loginTime;
}
