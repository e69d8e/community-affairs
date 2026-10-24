package com.li.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.community.common.constant.RedisKeyConstant;
import com.li.community.common.constant.RoleConstant;
import com.li.community.common.exception.BizException;
import com.li.community.common.exception.ErrorCode;
import com.li.community.common.result.PageResult;
import com.li.community.common.utils.SecurityUtils;
import com.li.community.mapper.SysUserMapper;
import com.li.community.model.dto.UserUpdateDto;
import com.li.community.model.entity.SysUser;
import com.li.community.model.vo.UserVo;
import com.li.community.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 记录用户级令牌吊销时间戳：该时刻之前签发的 token 全部失效，
     * 保证禁用、降权、改密能即时生效（无需等待 7 天过期）
     */
    private void revokeUserTokens(Long userId) {
        try {
            redisTemplate.opsForValue().set(
                    RedisKeyConstant.USER_BAN_PREFIX + userId, System.currentTimeMillis());
        } catch (Exception e) {
            // Redis 不可用时仅记录日志，不阻断管理操作本身
            log.warn("写入用户令牌吊销标记失败: userId={}, cause={}", userId, e.getMessage());
        }
    }

    @Override
    public void updateProfile(UserUpdateDto dto) {
        Long userId = SecurityUtils.getUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BizException(ErrorCode.USER_NOT_FOUND);

        if (dto.getRealName() != null) user.setRealName(dto.getRealName());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getAvatar() != null) user.setAvatar(dto.getAvatar());
        if (dto.getGender() != null) user.setGender(dto.getGender());

        userMapper.updateById(user);
    }

    @Override
    public void updatePassword(String oldPassword, String newPassword) {
        Long userId = SecurityUtils.getUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BizException(ErrorCode.USER_NOT_FOUND);

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "原密码校验错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        // 改密后旧设备 token 全部失效
        revokeUserTokens(userId);
    }

    @Override
    public PageResult<UserVo> listUsers(String keyword, String role, Long page, Long size) {
        Page<SysUser> pageParam = new Page<>(page != null ? page : 1, size != null ? size : 10);
        Page<SysUser> result = userMapper.selectPage(
                pageParam,
                new LambdaQueryWrapper<SysUser>()
                        .eq(role != null && !role.isEmpty(), SysUser::getRole, role)
                        .and(keyword != null && !keyword.trim().isEmpty(), w ->
                                w.like(SysUser::getUsername, keyword.trim())
                                        .or()
                                        .like(SysUser::getRealName, keyword.trim())
                                        .or()
                                        .like(SysUser::getPhone, keyword.trim()))
                        .orderByDesc(SysUser::getCreateTime)
        );

        List<UserVo> voList = result.getRecords().stream().map(u -> UserVo.builder()
                .id(u.getId())
                .username(u.getUsername())
                .realName(u.getRealName())
                .phone(u.getPhone())
                .avatar(u.getAvatar())
                .gender(u.getGender())
                .role(u.getRole())
                .status(u.getStatus())
                .creditScore(u.getCreditScore())
                .createTime(u.getCreateTime())
                .build()
        ).collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), result.getSize(), result.getCurrent());
    }

    @Override
    public void updateUserStatus(Long userId, Integer status) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BizException(ErrorCode.USER_NOT_FOUND);

        user.setStatus(status);
        userMapper.updateById(user);
        if (status != null && status == 0) {
            // 禁用账号后其所有 token 立即失效
            revokeUserTokens(userId);
        }
    }

    @Override
    public void updateUserRole(Long userId, String role) {
        // 只允许在系统既定的五种角色间分配，防止写入任意脏值
        Set<String> validRoles = Set.of(
                RoleConstant.ROLE_SYSTEM_ADMIN, RoleConstant.ROLE_COMMUNITY_ADMIN,
                RoleConstant.ROLE_PROPERTY_ADMIN, RoleConstant.ROLE_GRID_OFFICER,
                RoleConstant.ROLE_RESIDENT);
        if (role == null || !validRoles.contains(role)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法的角色编码: " + role);
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BizException(ErrorCode.USER_NOT_FOUND);

        user.setRole(role);
        userMapper.updateById(user);
        // 降权/授权后旧 token 中的角色已过期，强制重新登录
        revokeUserTokens(userId);
    }
}
