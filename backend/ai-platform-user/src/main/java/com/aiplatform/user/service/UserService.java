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
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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
}
