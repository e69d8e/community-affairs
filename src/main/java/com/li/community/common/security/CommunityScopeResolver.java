package com.li.community.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.community.common.exception.BizException;
import com.li.community.common.exception.ErrorCode;
import com.li.community.common.utils.SecurityUtils;
import com.li.community.mapper.BizCommunityMapper;
import com.li.community.model.entity.BizCommunity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 多社区(多租户)数据范围解析器: 统一处理"当前请求应看到哪个小区的数据"。
 * <p>
 * 平台管理员(SYSTEM_ADMIN, communityId=NULL)可通过入参指定目标小区, 其余角色一律强制使用本人所属小区(忽略入参, 防越权)。
 */
@Component
@RequiredArgsConstructor
public class CommunityScopeResolver {

    private final BizCommunityMapper communityMapper;

    /**
     * 管理端列表/导出场景: 平台管理员 param 为空返回 NULL(表示查看全局), 非空按指定小区过滤; 其余角色强制本人小区
     */
    public Long resolveAdminScope(Long param) {
        if (SecurityUtils.isSystemAdmin()) {
            return param;
        }
        return requireOwnCommunity();
    }

    /**
     * 创建类业务(发布公告/活动/楼栋等): 必须解析出具体小区。
     * 平台管理员必须显式指定 param, 其余角色强制使用本人小区
     */
    public Long resolveForCreate(Long param) {
        if (SecurityUtils.isSystemAdmin()) {
            if (param == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "平台管理员请指定目标小区(communityId)");
            }
            return param;
        }
        return requireOwnCommunity();
    }

    /**
     * 公开列表场景(公告/活动/二手/搜索):
     * 登录用户走管理范围规则; 匿名请求优先取入参 communityId,
     * 未携带时回退第一个启用小区(游客默认浏览视图), 仅当无任何启用小区时报错
     */
    public Long resolvePublicScope(Long param) {
        Authentication auth = SecurityUtils.getAuthentication();
        boolean loggedIn = auth != null && auth.getPrincipal() instanceof LoginUser;
        if (loggedIn) {
            return resolveAdminScope(param);
        }
        if (param != null) {
            return param;
        }
        BizCommunity fallback = communityMapper.selectOne(
                new LambdaQueryWrapper<BizCommunity>()
                        .eq(BizCommunity::getStatus, 1)
                        .orderByAsc(BizCommunity::getId)
                        .last("LIMIT 1"));
        if (fallback == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "暂无启用中的小区");
        }
        return fallback.getId();
    }

    private Long requireOwnCommunity() {
        Long own = SecurityUtils.getCommunityId();
        if (own == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前账号未归属任何小区，无法执行该操作");
        }
        return own;
    }

    /**
     * 校验值是否等于当前用户所属小区(用于详情类越权校验); 平台管理员直接放行
     */
    public boolean isInScope(Long communityId) {
        if (SecurityUtils.isSystemAdmin()) {
            return true;
        }
        return communityId != null && communityId.equals(SecurityUtils.getCommunityId());
    }

    /**
     * 校验指定小区存在且处于启用状态(注册/创建社区数据时使用)
     */
    public BizCommunity requireActiveCommunity(Long communityId) {
        BizCommunity community = communityMapper.selectById(communityId);
        if (community == null || community.getStatus() == null || community.getStatus() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "小区不存在或已停用");
        }
        return community;
    }
}
