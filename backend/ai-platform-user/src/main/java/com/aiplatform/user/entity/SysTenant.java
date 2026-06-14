package com.aiplatform.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_tenant")
public class SysTenant {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String tenantCode;
    private String tenantName;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private Integer status;
    private LocalDateTime expireTime;
    private String planCode;
    private Integer maxUsers;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
