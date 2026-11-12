package com.li.community.service.impl;

import cn.hutool.core.util.IdUtil;
import com.li.community.common.constant.RedisKeyConstant;
import com.li.community.common.exception.BizException;
import com.li.community.common.exception.ErrorCode;
import com.li.community.common.utils.SlideCaptchaUtil;
import com.li.community.model.vo.CaptchaVo;
import com.li.community.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    /** 滑块位置容差 (px) */
    private static final int SLIDE_TOLERANCE_PX = 5;
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(3);

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public CaptchaVo generate() {
        SlideCaptchaUtil.SlideCaptchaImage image = SlideCaptchaUtil.generate();
        String captchaId = IdUtil.fastSimpleUUID();
        redisTemplate.opsForValue().set(
                RedisKeyConstant.CAPTCHA_PREFIX + captchaId, image.targetX(), CAPTCHA_TTL
        );
        return CaptchaVo.builder()
                .captchaId(captchaId)
                .bgImage(image.bgImage())
                .jigsawImage(image.jigsawImage())
                .build();
    }

    @Override
    public void verify(String captchaId, Integer slideX) {
        if (!StringUtils.hasText(captchaId) || slideX == null) {
            throw new BizException(ErrorCode.CAPTCHA_VERIFY_FAILED);
        }
        // getAndDelete 原子取出并删除，保证验证码一次性使用 (失败也作废，需刷新重滑)
        Object expected = redisTemplate.opsForValue().getAndDelete(RedisKeyConstant.CAPTCHA_PREFIX + captchaId);
        if (expected == null) {
            throw new BizException(ErrorCode.CAPTCHA_EXPIRED);
        }
        if (Math.abs(slideX - ((Number) expected).intValue()) > SLIDE_TOLERANCE_PX) {
            throw new BizException(ErrorCode.CAPTCHA_VERIFY_FAILED);
        }
    }
}
