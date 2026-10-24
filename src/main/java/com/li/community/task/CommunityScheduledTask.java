package com.li.community.task;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.li.community.mapper.BizActivityMapper;
import com.li.community.mapper.BizOrderLogMapper;
import com.li.community.mapper.BizWorkOrderMapper;
import com.li.community.model.entity.BizActivity;
import com.li.community.model.entity.BizOrderLog;
import com.li.community.model.entity.BizWorkOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommunityScheduledTask {

    private final BizActivityMapper activityMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizOrderLogMapper orderLogMapper;

    /**
     * 每 60 秒检查并推进活动生命周期状态。
     * 使用仅更新 status 字段的条件 UPDATE，避免整实体 updateById 把并发报名
     * 刚写入的 currentParticipants 旧值刷回（丢计数）。
     */
    @Scheduled(fixedDelay = 60 * 1000)
    public void scanActivityLifecycle() {
        LocalDateTime now = LocalDateTime.now();

        // 1. 报名中 -> 报名截止待开展 (到达 signupEndTime)
        List<BizActivity> signupEndList = activityMapper.selectList(
                new LambdaQueryWrapper<BizActivity>()
                        .eq(BizActivity::getStatus, 1)
                        .le(BizActivity::getSignupEndTime, now)
        );
        for (BizActivity act : signupEndList) {
            activityMapper.update(null, new LambdaUpdateWrapper<BizActivity>()
                    .set(BizActivity::getStatus, 2)
                    .eq(BizActivity::getId, act.getId())
                    .eq(BizActivity::getStatus, 1));
            log.info("定时任务: 活动【{}】报名时间已截止，状态流转为待开展", act.getTitle());
        }

        // 2. 待开展 -> 进行中 (到达 startTime)
        List<BizActivity> startList = activityMapper.selectList(
                new LambdaQueryWrapper<BizActivity>()
                        .eq(BizActivity::getStatus, 2)
                        .le(BizActivity::getStartTime, now)
        );
        for (BizActivity act : startList) {
            activityMapper.update(null, new LambdaUpdateWrapper<BizActivity>()
                    .set(BizActivity::getStatus, 3)
                    .eq(BizActivity::getId, act.getId())
                    .eq(BizActivity::getStatus, 2));
            log.info("定时任务: 活动【{}】已正式开展，状态流转为进行中", act.getTitle());
        }

        // 3. 进行中 -> 已圆满结束 (到达 endTime)
        List<BizActivity> endList = activityMapper.selectList(
                new LambdaQueryWrapper<BizActivity>()
                        .eq(BizActivity::getStatus, 3)
                        .le(BizActivity::getEndTime, now)
        );
        for (BizActivity act : endList) {
            activityMapper.update(null, new LambdaUpdateWrapper<BizActivity>()
                    .set(BizActivity::getStatus, 4)
                    .eq(BizActivity::getId, act.getId())
                    .eq(BizActivity::getStatus, 3));
            log.info("定时任务: 活动【{}】已圆满结束", act.getTitle());
        }
    }

    /**
     * 每 10 分钟扫描超时未评工单 (完工满7天系统自动好评归档)
     */
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void scanTimeoutOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);

        List<BizWorkOrder> timeoutOrders = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>()
                        .eq(BizWorkOrder::getStatus, 5) // 5-已完工待评价
                        .le(BizWorkOrder::getFinishTime, threshold)
        );

        for (BizWorkOrder order : timeoutOrders) {
            // 仅更新评价相关字段，条件更新防止与居民并发评价冲突
            int updated = workOrderMapper.update(null, new LambdaUpdateWrapper<BizWorkOrder>()
                    .set(BizWorkOrder::getStatus, 6) // 已评价结单
                    .set(BizWorkOrder::getRatingStar, 5)
                    .set(BizWorkOrder::getRatingContent, "完工满7日业主未主动评价，系统自动好评归档结单")
                    .eq(BizWorkOrder::getId, order.getId())
                    .eq(BizWorkOrder::getStatus, 5));
            if (updated == 0) {
                continue;
            }

            orderLogMapper.insert(BizOrderLog.builder()
                    .id(IdUtil.getSnowflakeNextId())
                    .orderId(order.getId())
                    .actionType("AUTO_EVALUATE")
                    .fromStatus(5)
                    .toStatus(6)
                    .operatorId(0L)
                    .operatorName("系统自动化调度器")
                    .operatorRole("SYSTEM")
                    .remark("超时自动好评结单")
                    .build());

            log.info("定时任务: 工单【{}】完工超时7天，已自动完成好评结单归档", order.getOrderNo());
        }
    }
}
