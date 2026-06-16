package com.aiplatform.user.service;

import com.aiplatform.common.constant.CommonConstants;
import com.aiplatform.starter.mybatis.entity.PageQuery;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.starter.mybatis.support.PageAdapter;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.user.entity.SysUser;
import com.aiplatform.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final com.aiplatform.user.mapper.SysUserTenantMapper userTenantMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 绑定用户到公司 (写 sys_user_tenant 中间表).
     * 静默跳过重复绑定.
     */
    public void bindTenant(Long userId, Long tenantId) {
        com.aiplatform.user.entity.SysUserTenant rel = new com.aiplatform.user.entity.SysUserTenant();
        rel.setUserId(userId);
        rel.setTenantId(tenantId);
        try {
            userTenantMapper.insert(rel);
        } catch (org.springframework.dao.DuplicateKeyException ignore) {
            // 已存在, 跳过
        }
    }

    public Map<String, Object> getByUsername(String username) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("limit 1"));
        if (user == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("password", user.getPassword());
        map.put("nickname", user.getNickname());
        map.put("email", user.getEmail());
        map.put("phone", user.getPhone());
        map.put("avatar", user.getAvatar());
        map.put("status", user.getStatus());
        map.put("tenantId", user.getTenantId());
        map.put("department", user.getDepartment());
        return map;
    }

    public SysUser create(SysUser user) {
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名必填");
        }
        if (user.getPassword() == null) {
            user.setPassword("123456");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        if (user.getTenantId() == null) {
            user.setTenantId(CommonConstants.SUPER_TENANT_ID);
        }
        userMapper.insert(user);
        return user;
    }

    public PageResult<SysUser> page(PageQuery query) {
        Page<SysUser> page = query.toPage();
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(SysUser::getUsername, query.getKeyword())
                    .or().like(SysUser::getNickname, query.getKeyword());
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        return PageAdapter.of(userMapper.selectPage(page, wrapper));
    }

    public List<SysUser> listAll() {
        return userMapper.selectList(null);
    }

    public SysUser update(SysUser user) {
        if (user.getId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "ID 必填");
        }
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null);
        }
        userMapper.updateById(user);
        return userMapper.selectById(user.getId());
    }

    public void delete(Long id) {
        userMapper.deleteById(id);
    }

    /** 重置密码返 123456 */
    public String resetPassword(Long id) {
        String pwd = "123456";
        SysUser u = new SysUser();
        u.setId(id);
        u.setPassword(passwordEncoder.encode(pwd));
        userMapper.updateById(u);
        return pwd;
    }

    /** 自己改密码 */
    public void changePassword(Long id, String oldPwd, String newPwd) {
        if (oldPwd == null || newPwd == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新旧密码必填");
        }
        SysUser u = userMapper.selectById(id);
        if (u == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if (!passwordEncoder.matches(oldPwd, u.getPassword())) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR, "原密码错误");
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(newPwd));
        userMapper.updateById(update);
    }

    /** 启停 */
    public void changeStatus(Long id, Integer status) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setStatus(status);
        userMapper.updateById(u);
    }

    /** 统计: 总数 / 启用 / 停用 / 本月新增 */
    public Map<String, Object> stats() {
        Long total = userMapper.selectCount(null);
        Long active = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1));
        Long inactive = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 0));
        java.time.LocalDateTime monthStart = java.time.LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        Long thisMonth = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().ge(SysUser::getCreateTime, monthStart));
        return Map.of("total", total, "active", active, "inactive", inactive, "thisMonth", thisMonth);
    }
}
