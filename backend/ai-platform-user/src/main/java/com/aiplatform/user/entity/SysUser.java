package com.aiplatform.user.entity;

import com.aiplatform.starter.mybatis.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;
    private String password;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;

    @TableField("status")
    private Integer status;

    private String lastLoginIp;
    private java.time.LocalDateTime lastLoginTime;

    /**
     * 部门（员工所属部门，用字符串表示，如"研发部"/"市场部"）。
     * 同一公司（租户）下不同部门的人登录后看到同样的数据范围，
     * 但前端可按部门做标识（头像徽章 / 顶部 tag）。
     */
    private String department;
}
