package com.aiplatform.system.entity;

import com.aiplatform.starter.mybatis.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {
    private Long parentId;
    private String menuName;
    private String path;
    private String component;
    private String icon;
    private Integer menuType;
    private String permission;
    private Integer sortOrder;
    private Integer visible;
    private Integer status;
}
