package com.aiplatform.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user_tenant")
public class SysUserTenant {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long tenantId;
    private String roleInTenant;   // owner/admin/member/guest
    private Integer isDefault;      // 1=是, 0=否
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
