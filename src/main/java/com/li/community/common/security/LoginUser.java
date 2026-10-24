package com.li.community.common.security;

/**
 * 登录用户主体: 携带多社区(多租户)上下文, 替代 SecurityContext 中的裸 userId。
 * communityId 为 NULL 表示平台级账号(系统管理员), 可跨社区查看数据。
 */
public record LoginUser(Long userId, String username, String role, Long communityId) {

    /**
     * 保持既有行为: authentication.getName() 返回 userId 字符串
     */
    @Override
    public String toString() {
        return String.valueOf(userId);
    }
}
