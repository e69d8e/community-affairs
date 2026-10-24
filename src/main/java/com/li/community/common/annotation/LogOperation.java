package com.li.community.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LogOperation {
    /**
     * 模块名称 (如: 报修管理)
     */
    String title() default "";

    /**
     * 业务操作类型: INSERT, UPDATE, DELETE, DISPATCH, AUDIT, EXPORT
     */
    String businessType() default "OTHER";

    /**
     * 是否保存请求参数
     */
    boolean saveParam() default true;

    /**
     * 是否保存返回响应结果
     */
    boolean saveResult() default false;
}
