package com.li.community.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "请先登录或令牌已失效"),
    FORBIDDEN(403, "无权限访问此资源"),
    NOT_FOUND(404, "请求资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方式不支持"),
    INTERNAL_ERROR(500, "系统内部繁忙，请稍后再试"),

    // 用户与鉴权业务
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_PASSWORD_ERROR(1002, "账号或密码错误"),
    USER_DISABLED(1003, "该账号已被禁用，请联系管理员"),
    USERNAME_ALREADY_EXISTS(1004, "用户名已被占用"),
    PHONE_ALREADY_EXISTS(1005, "手机号已注册"),
    TOKEN_EXPIRED(1006, "登录状态已过期，请重新登录"),
    TOKEN_INVALID(1007, "无效的身份令牌"),

    // 工单业务
    WORK_ORDER_NOT_FOUND(2001, "工单不存在"),
    WORK_ORDER_STATUS_ERROR(2002, "当前工单状态不允许执行该流转操作"),
    WORK_ORDER_PERMISSION_DENIED(2003, "您无权操作此工单"),

    // 活动业务
    ACTIVITY_NOT_FOUND(3001, "社区活动不存在"),
    ACTIVITY_NOT_IN_SIGNUP_PERIOD(3002, "当前不在该活动的报名有效时间内"),
    ACTIVITY_FULL(3003, "该活动报名名额已满"),
    ACTIVITY_ALREADY_SIGNED_UP(3004, "您已报名该活动，请勿重复报名"),
    ACTIVITY_VERIFY_CODE_INVALID(3005, "核销凭证码无效或已被使用"),

    // 内容与敏感词
    CONTENT_SENSITIVE_REJECT(4001, "发布内容包含违禁敏感词汇，已被系统安全拦截"),
    POST_NOT_FOUND(4002, "帖子或闲置物品不存在"),

    // 文件与存储
    FILE_UPLOAD_ERROR(5001, "文件上传失败，请检查存储服务状态"),
    FILE_TYPE_NOT_SUPPORTED(5002, "暂不支持该文件格式上传");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
