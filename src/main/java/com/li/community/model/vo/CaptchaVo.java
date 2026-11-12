package com.li.community.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 登录/注册滑块验证码出参
 */
@Data
@Builder
public class CaptchaVo {

    /** 本次验证码会话 ID (提交登录/注册时原样带回) */
    private String captchaId;

    /** 带缺口背景图 (data:image/png;base64,...) */
    private String bgImage;

    /** 拼图块图 (透明画布含真实 y 坐标, 前端仅需水平移动) */
    private String jigsawImage;
}
