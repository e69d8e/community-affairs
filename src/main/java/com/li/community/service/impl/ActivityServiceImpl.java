package com.li.community.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.community.common.constant.RedisKeyConstant;
import com.li.community.common.exception.BizException;
import com.li.community.common.exception.ErrorCode;
import com.li.community.common.result.PageResult;
import com.li.community.common.security.CommunityScopeResolver;
import com.li.community.common.utils.QRCodeUtil;
import com.li.community.common.utils.SecurityUtils;
import com.li.community.mapper.BizActivityMapper;
import com.li.community.mapper.BizActivitySignupMapper;
import com.li.community.mapper.SysUserMapper;
import com.li.community.mapper.SysUserNoticeMapper;
import com.li.community.model.dto.ActivityCreateDto;
import com.li.community.model.dto.ActivityVerifyDto;
import com.li.community.model.entity.BizActivity;
import com.li.community.model.entity.BizActivitySignup;
import com.li.community.model.entity.SysUser;
import com.li.community.model.entity.SysUserNotice;
import com.li.community.model.vo.ActivityVo;
import com.li.community.service.ActivityService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    private final BizActivityMapper activityMapper;
    private final BizActivitySignupMapper signupMapper;
    private final SysUserMapper userMapper;
    private final SysUserNoticeMapper userNoticeMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final CommunityScopeResolver scopeResolver;

    private DefaultRedisScript<Long> signupRedisScript;

    // 活动状态: 1-报名中 2-报名截止待开展 3-进行中 4-已结束
    private static final Set<Integer> ACTIVITY_STATUSES = Set.of(1, 2, 3, 4);

    @PostConstruct
    public void initScript() {
        signupRedisScript = new DefaultRedisScript<>();
        signupRedisScript.setLocation(new ClassPathResource("lua/activity_signup.lua"));
        signupRedisScript.setResultType(Long.class);
    }

    @Override
    public PageResult<ActivityVo> listActivities(String category, Integer status, Long communityId, Long page, Long size) {
        Page<BizActivity> pageParam = new Page<>(page != null ? page : 1, size != null ? size : 10);

        Long communityScope = scopeResolver.resolvePublicScope(communityId);
        Page<BizActivity> result = activityMapper.selectPage(
                pageParam,
                new LambdaQueryWrapper<BizActivity>()
                        .eq(BizActivity::getCommunityId, communityScope)
                        .eq(category != null && !category.isEmpty(), BizActivity::getCategory, category)
                        .eq(status != null, BizActivity::getStatus, status)
                        .orderByDesc(BizActivity::getCreateTime)
        );

        Long currentUserId = null;
        try {
            currentUserId = SecurityUtils.getUserId();
        } catch (Exception ignored) {}

        List<ActivityVo> voList = convertToVoList(result.getRecords(), currentUserId, false);

        return PageResult.of(voList, result.getTotal(), result.getSize(), result.getCurrent());
    }

    @Override
    public ActivityVo getActivityDetail(Long activityId) {
        BizActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        Long currentUserId = null;
        try {
            currentUserId = SecurityUtils.getUserId();
        } catch (Exception ignored) {}

        List<ActivityVo> voList = convertToVoList(List.of(activity), currentUserId, true);
        return voList.get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityVo signupActivity(Long activityId) {
        Long userId = SecurityUtils.getUserId();
        SysUser user = userMapper.selectById(userId);

        BizActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        if (activity.getSignupStartTime() != null && now.isBefore(activity.getSignupStartTime())) {
            throw new BizException(ErrorCode.ACTIVITY_NOT_IN_SIGNUP_PERIOD, "活动报名尚未开启");
        }
        if (activity.getSignupEndTime() != null && now.isAfter(activity.getSignupEndTime())) {
            throw new BizException(ErrorCode.ACTIVITY_NOT_IN_SIGNUP_PERIOD, "活动报名已截止");
        }
        if (activity.getStatus() != 1) {
            throw new BizException(ErrorCode.ACTIVITY_NOT_IN_SIGNUP_PERIOD, "当前活动状态不支持报名");
        }

        // Redis Lua 原子防超卖扣减
        String quotaKey = RedisKeyConstant.ACTIVITY_QUOTA_PREFIX + activityId;
        String usersKey = RedisKeyConstant.ACTIVITY_USERS_PREFIX + activityId;

        // 配额缓存缺失时用 SET NX 初始化：并发下只有一个请求写入，不会覆盖已扣减的值
        int remaining = Math.max(0, activity.getMaxParticipants() - activity.getCurrentParticipants());
        redisTemplate.opsForValue().setIfAbsent(quotaKey, remaining);

        Long luaResult = redisTemplate.execute(signupRedisScript, List.of(quotaKey, usersKey), userId.toString());

        if (luaResult == null || luaResult == 0) {
            throw new BizException(ErrorCode.ACTIVITY_FULL);
        }
        if (luaResult == -1) {
            throw new BizException(ErrorCode.ACTIVITY_ALREADY_SIGNED_UP);
        }

        try {
            // DB 条件原子自增兜底：并发绕过 Redis 时仍不超卖；失败则视为名额已满
            int updated = activityMapper.update(null, new LambdaUpdateWrapper<BizActivity>()
                    .setSql("current_participants = current_participants + 1")
                    .eq(BizActivity::getId, activityId)
                    .apply("current_participants < max_participants"));
            if (updated == 0) {
                throw new BizException(ErrorCode.ACTIVITY_FULL);
            }

            // 生成专属8位核销码（查重防撞码）与二维码
            String verifyCode = generateUniqueVerifyCode();
            String qrContent = "COMMUNITY:ACT:" + activityId + ":" + verifyCode;
            String qrCodeBase64 = QRCodeUtil.generateBase64QRCode(qrContent, 200, 200);

            BizActivitySignup existing = signupMapper.selectOne(
                    new LambdaQueryWrapper<BizActivitySignup>()
                            .eq(BizActivitySignup::getActivityId, activityId)
                            .eq(BizActivitySignup::getUserId, userId)
            );
            if (existing != null) {
                existing.setStatus(1); // 1-已成功报名
                existing.setVerifyCode(verifyCode);
                existing.setQrCodeContent(qrContent);
                existing.setSignInTime(null);
                existing.setVerifierId(null);
                existing.setVerifierName("");
                existing.setFeedbackStar(null);
                existing.setFeedbackContent("");
                signupMapper.updateById(existing);
            } else {
                BizActivitySignup signup = BizActivitySignup.builder()
                        .id(IdUtil.getSnowflakeNextId())
                        .activityId(activityId)
                        .userId(userId)
                        .residentName(user != null ? user.getRealName() : "居民")
                        .residentPhone(user != null ? user.getPhone() : "")
                        .verifyCode(verifyCode)
                        .qrCodeContent(qrContent)
                        .status(1) // 1-已成功报名
                        .build();
                signupMapper.insert(signup);
            }

            // 写入站内信提醒
            userNoticeMapper.insert(SysUserNotice.builder()
                    .id(IdUtil.getSnowflakeNextId())
                    .userId(userId)
                    .noticeType("ACTIVITY")
                    .title("活动报名成功核销凭证")
                    .summary("您已成功报名【" + activity.getTitle() + "】，现场签到核销码【" + verifyCode + "】。")
                    .bizId(activityId)
                    .isRead(0)
                    .build());

            // VO 中的报名数展示为自增后的值
            activity.setCurrentParticipants(activity.getCurrentParticipants() + 1);

            ActivityVo vo = convertToVoList(List.of(activity), userId, true).get(0);
            vo.setHasSignedUp(true);
            vo.setMySignupStatus(1);
            vo.setMyVerifyCode(verifyCode);
            vo.setMyQrCodeBase64(qrCodeBase64);

            return vo;
        } catch (Exception e) {
            // DB 回滚后回补 Redis 名额并移除用户标记，避免名额永久泄漏、用户被"锁死"
            rollbackSignupQuota(quotaKey, usersKey, userId);
            throw e;
        }
    }

    /**
     * 生成 8 位数字核销码并查重；极小概率连续撞码时兜底加长
     */
    private String generateUniqueVerifyCode() {
        for (int i = 0; i < 3; i++) {
            String code = RandomUtil.randomNumbers(8);
            Long count = signupMapper.selectCount(
                    new LambdaQueryWrapper<BizActivitySignup>()
                            .eq(BizActivitySignup::getVerifyCode, code));
            if (count == null || count == 0) {
                return code;
            }
        }
        return RandomUtil.randomNumbers(10);
    }

    /**
     * Redis 配额回补：配额键存在则精确回补名额并移除用户标记；
     * 键已丢失则成对清空，让下一次报名从 DB 重建缓存。
     */
    private void rollbackSignupQuota(String quotaKey, String usersKey, Long userId) {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(quotaKey))) {
                redisTemplate.opsForValue().increment(quotaKey);
                redisTemplate.opsForSet().remove(usersKey, userId.toString());
            } else {
                redisTemplate.delete(List.of(quotaKey, usersKey));
            }
        } catch (Exception ex) {
            log.warn("回补活动报名 Redis 名额失败，清除配额缓存待重建: {}", ex.getMessage());
            try {
                redisTemplate.delete(List.of(quotaKey, usersKey));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelSignup(Long activityId) {
        Long userId = SecurityUtils.getUserId();
        BizActivitySignup signup = signupMapper.selectOne(
                new LambdaQueryWrapper<BizActivitySignup>()
                        .eq(BizActivitySignup::getActivityId, activityId)
                        .eq(BizActivitySignup::getUserId, userId)
                        .eq(BizActivitySignup::getStatus, 1)
        );

        if (signup == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "未找到有效的活动报名记录");
        }

        signup.setStatus(3); // 3-已取消
        signupMapper.updateById(signup);

        // SQL 原子扣减报名人数，避免并发取消/报名读改写互相覆盖
        activityMapper.update(null, new LambdaUpdateWrapper<BizActivity>()
                .setSql("current_participants = current_participants - 1")
                .eq(BizActivity::getId, activityId)
                .apply("current_participants > 0"));

        // Redis 回补名额并移除用户
        String quotaKey = RedisKeyConstant.ACTIVITY_QUOTA_PREFIX + activityId;
        String usersKey = RedisKeyConstant.ACTIVITY_USERS_PREFIX + activityId;
        rollbackSignupQuota(quotaKey, usersKey, userId);
    }

    @Override
    public List<ActivityVo> getMySignups() {
        Long userId = SecurityUtils.getUserId();
        List<BizActivitySignup> signups = signupMapper.selectList(
                new LambdaQueryWrapper<BizActivitySignup>()
                        .eq(BizActivitySignup::getUserId, userId)
                        .ne(BizActivitySignup::getStatus, 3)
                        .orderByDesc(BizActivitySignup::getCreateTime)
        );
        if (signups.isEmpty()) {
            return new ArrayList<>();
        }

        // 批量加载活动，避免逐条回表
        Map<Long, BizActivity> activityMap = activityMapper.selectBatchIds(
                        signups.stream().map(BizActivitySignup::getActivityId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(BizActivity::getId, a -> a, (a, b) -> a));

        List<ActivityVo> result = new ArrayList<>();
        for (BizActivitySignup s : signups) {
            BizActivity act = activityMap.get(s.getActivityId());
            if (act != null) {
                ActivityVo vo = convertToVoList(List.of(act), userId, true).get(0);
                vo.setHasSignedUp(true);
                vo.setMySignupStatus(s.getStatus());
                vo.setMyVerifyCode(s.getVerifyCode());
                vo.setMyQrCodeBase64(QRCodeUtil.generateBase64QRCode(s.getQrCodeContent(), 200, 200));
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizActivitySignup verifySignup(ActivityVerifyDto dto) {
        Long verifierId = SecurityUtils.getUserId();
        String code = dto.getVerifyCode().trim();

        // 支持输入6位核销码或扫描动态二维码串
        BizActivitySignup signup = signupMapper.selectOne(
                new LambdaQueryWrapper<BizActivitySignup>()
                        .eq(BizActivitySignup::getVerifyCode, code)
                        .or()
                        .eq(BizActivitySignup::getQrCodeContent, code)
        );

        if (signup == null) {
            throw new BizException(ErrorCode.ACTIVITY_VERIFY_CODE_INVALID);
        }
        if (signup.getStatus() == 2) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该凭证已于 " + signup.getSignInTime() + " 完成签到，请勿重复核销");
        }
        if (signup.getStatus() == 3) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该居民已取消此次活动报名");
        }

        signup.setStatus(2); // 2-已核销签到
        signup.setSignInTime(LocalDateTime.now());
        signup.setVerifierId(verifierId);
        signup.setVerifierName(SecurityUtils.getUsername());
        signupMapper.updateById(signup);

        // STOMP 推送签到成功消息给居民（事务提交后再推，避免回滚后误报）
        Runnable notify = () -> {
            try {
                messagingTemplate.convertAndSendToUser(
                        signup.getUserId().toString(),
                        "/queue/notify",
                        Map.of(
                                "type", "ACTIVITY_SIGNIN",
                                "title", "活动签到成功通知",
                                "summary", "您报名的活动现场签到核销成功！感谢您的积极参与。"
                        )
                );
            } catch (Exception e) {
                log.warn("发送签到通知失败: {}", e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notify.run();
                }
            });
        } else {
            notify.run();
        }

        return signup;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizActivity createActivity(ActivityCreateDto dto) {
        long activityId = IdUtil.getSnowflakeNextId();
        BizActivity activity = BizActivity.builder()
                .id(activityId)
                .title(dto.getTitle())
                .coverImage(dto.getCoverImage())
                .category(dto.getCategory())
                .communityId(scopeResolver.resolveForCreate(dto.getCommunityId()))
                .location(dto.getLocation())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .signupStartTime(dto.getSignupStartTime())
                .signupEndTime(dto.getSignupEndTime())
                .maxParticipants(dto.getMaxParticipants())
                .currentParticipants(0)
                .status(1) // 1-报名中
                .organizer(dto.getOrganizer() != null ? dto.getOrganizer() : "社区居委会")
                .contactPhone(dto.getContactPhone() != null ? dto.getContactPhone() : "")
                .description(dto.getDescription())
                .build();

        activityMapper.insert(activity);

        // 初始化 Redis 配额
        String quotaKey = RedisKeyConstant.ACTIVITY_QUOTA_PREFIX + activityId;
        redisTemplate.opsForValue().set(quotaKey, dto.getMaxParticipants());

        return activity;
    }

    @Override
    public void updateActivityStatus(Long activityId, Integer status) {
        if (status == null || !ACTIVITY_STATUSES.contains(status)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法的活动状态值");
        }
        BizActivity activity = activityMapper.selectById(activityId);
        if (activity == null) throw new BizException(ErrorCode.ACTIVITY_NOT_FOUND);

        activityMapper.update(null, new LambdaUpdateWrapper<BizActivity>()
                .set(BizActivity::getStatus, status)
                .eq(BizActivity::getId, activityId));
    }

    @Override
    public void exportSignupsExcel(Long activityId, HttpServletResponse response) {
        try {
            BizActivity activity = activityMapper.selectById(activityId);
            String title = activity != null ? activity.getTitle() : "社区活动";

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode(title + "_报名签到花名册", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

            List<BizActivitySignup> list = signupMapper.selectList(
                    new LambdaQueryWrapper<BizActivitySignup>()
                            .eq(BizActivitySignup::getActivityId, activityId)
                            .orderByAsc(BizActivitySignup::getCreateTime)
            );

            List<SignupExcelModel> excelList = list.stream().map(s -> SignupExcelModel.builder()
                    .residentName(s.getResidentName())
                    .residentPhone(s.getResidentPhone())
                    .verifyCode(s.getVerifyCode())
                    .statusName(s.getStatus() == 2 ? "已现场签到" : (s.getStatus() == 1 ? "已成功报名" : "已取消"))
                    .signInTime(s.getSignInTime() != null ? s.getSignInTime().toString() : "未签到")
                    .feedbackStar(s.getFeedbackStar() != null ? s.getFeedbackStar() + "星" : "未评")
                    .signupTime(s.getCreateTime() != null ? s.getCreateTime().toString() : "")
                    .build()
            ).collect(Collectors.toList());

            EasyExcel.write(response.getOutputStream(), SignupExcelModel.class)
                    .sheet("签到花名册")
                    .doWrite(excelList);
        } catch (IOException e) {
            log.error("导出活动花名册异常: ", e);
            throw new BizException("导出花名册失败: " + e.getMessage());
        }
    }

    /**
     * 批量转换 VO：当前用户的报名状态一次 IN 查询获取；
     * withQrCode=false 时（列表场景）不再逐行生成二维码，避免无谓的 CPU 编码
     */
    private List<ActivityVo> convertToVoList(List<BizActivity> activities, Long currentUserId, boolean withQrCode) {
        if (activities == null || activities.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, BizActivitySignup> mySignupMap = Map.of();
        if (currentUserId != null) {
            List<BizActivitySignup> signups = signupMapper.selectList(
                    new LambdaQueryWrapper<BizActivitySignup>()
                            .eq(BizActivitySignup::getUserId, currentUserId)
                            .in(BizActivitySignup::getActivityId,
                                    activities.stream().map(BizActivity::getId).collect(Collectors.toSet()))
            );
            // 一个用户对一个活动可能存在多条历史记录时保留最新一条
            mySignupMap = signups.stream().collect(Collectors.toMap(
                    BizActivitySignup::getActivityId, s -> s, (a, b) -> b));
        }

        Map<Long, BizActivitySignup> finalMySignupMap = mySignupMap;
        return activities.stream().map(act -> {
            ActivityVo vo = ActivityVo.builder()
                    .id(act.getId())
                    .title(act.getTitle())
                    .coverImage(act.getCoverImage())
                    .category(act.getCategory())
                    .communityId(act.getCommunityId())
                    .location(act.getLocation())
                    .startTime(act.getStartTime())
                    .endTime(act.getEndTime())
                    .signupStartTime(act.getSignupStartTime())
                    .signupEndTime(act.getSignupEndTime())
                    .maxParticipants(act.getMaxParticipants())
                    .currentParticipants(act.getCurrentParticipants())
                    .status(act.getStatus())
                    .organizer(act.getOrganizer())
                    .contactPhone(act.getContactPhone())
                    .description(act.getDescription())
                    .hasSignedUp(false)
                    .build();

            BizActivitySignup signup = finalMySignupMap.get(act.getId());
            if (signup != null && (signup.getStatus() == 1 || signup.getStatus() == 2)) {
                vo.setHasSignedUp(true);
                vo.setMySignupStatus(signup.getStatus());
                vo.setMyVerifyCode(signup.getVerifyCode());
                if (withQrCode) {
                    vo.setMyQrCodeBase64(QRCodeUtil.generateBase64QRCode(signup.getQrCodeContent(), 200, 200));
                }
            } else if (signup != null) {
                vo.setMySignupStatus(signup.getStatus());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignupExcelModel {
        @com.alibaba.excel.annotation.ExcelProperty("居民姓名")
        private String residentName;
        @com.alibaba.excel.annotation.ExcelProperty("联系电话")
        private String residentPhone;
        @com.alibaba.excel.annotation.ExcelProperty("专属核销码")
        private String verifyCode;
        @com.alibaba.excel.annotation.ExcelProperty("签到状态")
        private String statusName;
        @com.alibaba.excel.annotation.ExcelProperty("现场签到时间")
        private String signInTime;
        @com.alibaba.excel.annotation.ExcelProperty("活动反馈星级")
        private String feedbackStar;
        @com.alibaba.excel.annotation.ExcelProperty("报名提交时间")
        private String signupTime;
    }
}
