package com.aiplatform.user.service;

import com.aiplatform.common.constant.CommonConstants;
import com.aiplatform.starter.mybatis.entity.PageQuery;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.PageResult;
import com.aiplatform.starter.mybatis.support.PageAdapter;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.redis.distributed.DistributedCache;
import com.aiplatform.user.entity.SysUser;
import com.aiplatform.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ★ v3.x 性能优化: 用户高频读加 Redis 缓存.
 * <ul>
 *   <li>getByUsername() - 登录最频繁 (每次登录都查)</li>
 *   <li>listAll() - 用户列表 (admin 页面)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String CACHE_KEY_LIST = "aiplatform:user:list";
    private static final String CACHE_KEY_PREFIX_USERNAME = "aiplatform:user:username:";
    private static final String CACHE_KEY_PREFIX_ID = "aiplatform:user:id:";
    private static final int CACHE_TTL_SEC = 300;

    private final SysUserMapper userMapper;
    private final DistributedCache cache;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public Map<String, Object> getByUsername(String username) {
        String key = CACHE_KEY_PREFIX_USERNAME + username;
        return cache.getOrLoadJson(key, CACHE_TTL_SEC, () -> {
            SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, username)
                    .last("limit 1"));
            if (user == null) return null;
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
        }, new TypeReference<Map<String, Object>>() {});
    }

    public SysUser create(SysUser user) {
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        if (user.getStatus() == null) user.setStatus(1);
        if (user.getTenantId() == null) user.setTenantId(1L);
        userMapper.insert(user);
        cache.invalidate(CACHE_KEY_LIST);
        return user;
    }

    public SysUser getById(Long id) {
        String key = CACHE_KEY_PREFIX_ID + id;
        return cache.getOrLoad(key, CACHE_TTL_SEC,
            () -> userMapper.selectById(id), SysUser.class);
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
        // 清缓存
        if (user.getUsername() != null) {
            cache.invalidate(CACHE_KEY_PREFIX_USERNAME + user.getUsername());
        }
        cache.invalidate(CACHE_KEY_PREFIX_ID + user.getId());
        cache.invalidate(CACHE_KEY_LIST);
        return userMapper.selectById(user.getId());
    }

    public List<SysUser> listAll() {
        return cache.getOrLoadJson(CACHE_KEY_LIST, CACHE_TTL_SEC,
            () -> userMapper.selectList(null),
            new TypeReference<List<SysUser>>() {});
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

    public void delete(Long id) {
        userMapper.deleteById(id);
        cache.invalidate(CACHE_KEY_LIST);
        cache.invalidate(CACHE_KEY_PREFIX_ID + id);
    }
}