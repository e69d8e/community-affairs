package com.li.community.filter;

import com.li.community.common.constant.RedisKeyConstant;
import com.li.community.common.security.LoginUser;
import com.li.community.common.utils.JwtUtils;
import com.li.community.mapper.SysUserMapper;
import com.li.community.model.entity.SysUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SysUserMapper sysUserMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            authenticate(authHeader.substring(7), request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }
        Claims claims = jwtUtils.parseToken(token);
        if (claims == null || claims.getExpiration() == null || claims.getExpiration().before(new Date())) {
            return;
        }
        Long userId = jwtUtils.getUserIdOfClaims(claims);
        if (userId == null || isTokenRevoked(token, userId, claims)) {
            return;
        }

        String role = jwtUtils.getRoleOfClaims(claims);
        Long communityId = jwtUtils.getCommunityIdOfClaims(claims);
        boolean platformAccount = Boolean.TRUE.equals(claims.get("platform"));
        // 旧版本令牌未携带社区信息: 回查用户表自愈, 避免强制所有用户重新登录
        if (communityId == null && !platformAccount) {
            SysUser user = sysUserMapper.selectById(userId);
            if (user != null) {
                communityId = user.getCommunityId();
            }
        }

        LoginUser principal = new LoginUser(userId, claims.getSubject(), role, communityId);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        role != null ? Collections.singletonList(new SimpleGrantedAuthority(role)) : Collections.emptyList()
                );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * 校验 token 是否已被吊销（登出黑名单 / 用户被禁用、降权、改密）。
     * Redis 不可用时降级为仅签名校验，避免缓存故障导致全站不可用。
     */
    private boolean isTokenRevoked(String token, Long userId, Claims claims) {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeyConstant.TOKEN_BLACKLIST_PREFIX + token))) {
                log.warn("检测到黑名单中的已注销令牌: userId={}", userId);
                return true;
            }
            Object banAt = redisTemplate.opsForValue().get(RedisKeyConstant.USER_BAN_PREFIX + userId);
            if (banAt != null && claims.getIssuedAt() != null) {
                long banTime = Long.parseLong(String.valueOf(banAt));
                if (claims.getIssuedAt().getTime() < banTime) {
                    log.warn("用户已被禁用/降权/改密，旧令牌失效: userId={}", userId);
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("令牌吊销状态查询失败，降级为仅签名校验: {}", e.getMessage());
        }
        return false;
    }
}
