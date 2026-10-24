package com.li.community.common.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresRoles {
    /**
     * 允许访问的角色列表（满足其中之一即可）
     */
    String[] value();
}
