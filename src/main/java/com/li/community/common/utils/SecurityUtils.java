package com.li.community.common.utils;

import com.li.community.common.constant.RoleConstant;
import com.li.community.common.exception.BizException;
import com.li.community.common.exception.ErrorCode;
import com.li.community.common.security.LoginUser;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;

public class SecurityUtils {

    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * 是否已登录(排除匿名请求)
     */
    public static boolean isLoggedIn() {
        Authentication authentication = getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof LoginUser;
    }

    public static LoginUser getLoginUser() {
        Authentication authentication = getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return loginUser;
    }

    public static String getUsername() {
        if (isLoggedIn()) {
            return getLoginUser().username();
        }
        Authentication authentication = getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return authentication.getName();
    }

    public static Long getUserId() {
        Authentication authentication = getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser loginUser) {
            return loginUser.userId();
        }
        if (principal instanceof Number number) {
            return number.longValue();
        }
        if (principal instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    public static String getRole() {
        Authentication authentication = getAuthentication();
        if (authentication == null) return null;
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities != null && !authorities.isEmpty()) {
            return authorities.iterator().next().getAuthority();
        }
        return null;
    }

    public static boolean hasRole(String role) {
        String currentRole = getRole();
        return currentRole != null && currentRole.equals(role);
    }

    /**
     * 当前用户所属小区ID; NULL 表示平台级账号(系统管理员)
     */
    public static Long getCommunityId() {
        Authentication authentication = getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.communityId();
        }
        return null;
    }

    public static boolean isSystemAdmin() {
        return hasRole(RoleConstant.ROLE_SYSTEM_ADMIN);
    }
}
