package com.li.community.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.community.common.constant.RedisKeyConstant;
import com.li.community.common.constant.RoleConstant;
import com.li.community.common.exception.BizException;
import com.li.community.common.exception.ErrorCode;
import com.li.community.common.utils.JwtUtils;
import com.li.community.common.utils.SecurityUtils;
import com.li.community.mapper.*;
import com.li.community.model.dto.AuthLoginDto;
import com.li.community.model.dto.AuthRegisterDto;
import com.li.community.model.entity.*;
import com.li.community.model.vo.LoginVo;
import com.li.community.model.vo.UserVo;
import com.li.community.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final BizResidentHouseMapper residentHouseMapper;
    private final BizHouseMapper houseMapper;
    private final BizBuildingMapper buildingMapper;
    private final BizUnitMapper unitMapper;
    private final BizCommunityMapper communityMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public LoginVo login(AuthLoginDto dto) {
        String inputUsername = dto.getUsername() != null ? dto.getUsername().trim() : "";
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, inputUsername)
        );

        // 别名兼容：社区管理员与物业管理员支持常用缩写与全称互认 (comm_admin <-> community_admin, prop_admin <-> property_admin)
        if (user == null) {
            String aliasUsername = null;
            if ("comm_admin".equalsIgnoreCase(inputUsername)) {
                aliasUsername = "community_admin";
            } else if ("community_admin".equalsIgnoreCase(inputUsername)) {
                aliasUsername = "comm_admin";
            } else if ("prop_admin".equalsIgnoreCase(inputUsername)) {
                aliasUsername = "property_admin";
            } else if ("property_admin".equalsIgnoreCase(inputUsername)) {
                aliasUsername = "prop_admin";
            }
            if (aliasUsername != null) {
                user = userMapper.selectOne(
                        new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, aliasUsername)
                );
            }
        }

        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.USER_PASSWORD_ERROR);
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }

        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());

        return LoginVo.builder()
                .token(token)
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .gender(user.getGender())
                .role(user.getRole())
                .creditScore(user.getCreditScore())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVo register(AuthRegisterDto dto) {
        // 校验用户名是否重复
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername())
        );
        if (count > 0) {
            throw new BizException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }

        // 校验手机号是否重复
        Long phoneCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, dto.getPhone())
        );
        if (phoneCount > 0) {
            throw new BizException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        long userId = IdUtil.getSnowflakeNextId();
        SysUser user = SysUser.builder()
                .id(userId)
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword()))
                .realName(dto.getRealName())
                .phone(dto.getPhone())
                .avatar("https://api.dicebear.com/7.x/avataaars/svg?seed=" + dto.getUsername())
                .gender(2)
                .role(RoleConstant.ROLE_RESIDENT)
                .status(1)
                .creditScore(100)
                .build();

        userMapper.insert(user);

        // 绑定普通居民角色
        SysRole residentRole = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, RoleConstant.ROLE_RESIDENT)
        );
        if (residentRole != null) {
            userRoleMapper.insert(SysUserRole.builder()
                    .id(IdUtil.getSnowflakeNextId())
                    .userId(userId)
                    .roleId(residentRole.getId())
                    .build());
        }

        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());

        return LoginVo.builder()
                .token(token)
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .gender(user.getGender())
                .role(user.getRole())
                .creditScore(user.getCreditScore())
                .build();
    }

    @Override
    public void logout() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                // 放入 Redis 黑名单缓存，默认有效期7天
                redisTemplate.opsForValue().set(
                        RedisKeyConstant.TOKEN_BLACKLIST_PREFIX + token,
                        "1",
                        7,
                        TimeUnit.DAYS
                );
                log.info("用户成功退出，令牌已写入黑名单: {}", token);
            }
        }
    }

    @Override
    public UserVo getCurrentUser() {
        Long userId = SecurityUtils.getUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 查询用户绑定的房产
        List<BizResidentHouse> bindings = residentHouseMapper.selectList(
                new LambdaQueryWrapper<BizResidentHouse>().eq(BizResidentHouse::getUserId, userId)
        );

        List<UserVo.HouseInfoVo> houseList = new ArrayList<>();
        for (BizResidentHouse binding : bindings) {
            BizHouse house = houseMapper.selectById(binding.getHouseId());
            if (house != null) {
                BizCommunity comm = communityMapper.selectById(house.getCommunityId());
                BizBuilding bld = buildingMapper.selectById(house.getBuildingId());
                BizUnit unit = unitMapper.selectById(house.getUnitId());

                houseList.add(UserVo.HouseInfoVo.builder()
                        .houseId(house.getId())
                        .communityName(comm != null ? comm.getName() : "")
                        .buildingName(bld != null ? bld.getBuildingName() : "")
                        .unitName(unit != null ? unit.getUnitName() : "")
                        .houseNo(house.getHouseNo())
                        .identityType(binding.getIdentityType())
                        .certStatus(binding.getCertStatus())
                        .build());
            }
        }

        return UserVo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .gender(user.getGender())
                .role(user.getRole())
                .status(user.getStatus())
                .creditScore(user.getCreditScore())
                .createTime(user.getCreateTime())
                .boundHouses(houseList)
                .build();
    }
}
