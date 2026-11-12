package com.li.community.service;

import com.li.community.model.vo.CaptchaVo;

/**
 * 登录/注册滑块验证码服务
 */
public interface CaptchaService {

    /**
     * 生成滑块验证码 (背景图 + 拼图块)，缺口 x 坐标写入 Redis (3 分钟有效)
     */
    CaptchaVo generate();

    /**
     * 校验滑动位置并立即销毁该验证码 (一次性，无论成败)
     */
    void verify(String captchaId, Integer slideX);
}
