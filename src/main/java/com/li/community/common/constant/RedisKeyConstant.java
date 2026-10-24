package com.li.community.common.constant;

public interface RedisKeyConstant {
    // 用户令牌黑名单 (登出)
    String TOKEN_BLACKLIST_PREFIX = "community:token:blacklist:";

    // 用户级令牌吊销时间戳 (禁用/降权/改密后旧 token 失效)
    String USER_BAN_PREFIX = "community:token:user_ban:";

    // 活动报名名额与用户集合
    String ACTIVITY_QUOTA_PREFIX = "community:activity:quota:";
    String ACTIVITY_USERS_PREFIX = "community:activity:users:";

    // 敏感词前缀树缓存
    String SENSITIVE_WORDS_CACHE = "community:sensitive:words";

    // 搜索热词 ZSet
    String SEARCH_HOT_WORDS = "community:search:hot_words";

    // 接口防刷限流
    String RATE_LIMIT_PREFIX = "community:ratelimit:";
}
