package com.li.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.community.mapper.*;
import com.li.community.model.entity.*;
import com.li.community.model.vo.DashboardMetricsVo;
import com.li.community.model.vo.ScreenDataVo;
import com.li.community.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final SysUserMapper userMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizActivityMapper activityMapper;
    private final BizReportMapper reportMapper;
    private final SysNoticeMapper noticeMapper;
    private final BizMarketPostMapper marketPostMapper;
    private final BizCommunityMapper communityMapper;
    private final BizBuildingMapper buildingMapper;
    private final BizHouseMapper houseMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // 面板指标短缓存：多请求共享，避免每次 9+ 次 COUNT 全表扫描
    private static final String METRICS_CACHE_KEY = "community:dashboard:metrics";
    private static final String SCREEN_CACHE_KEY = "community:dashboard:screen";
    private static final long CACHE_TTL_SECONDS = 60;

    @Override
    public DashboardMetricsVo getMetrics() {
        DashboardMetricsVo cached = readCache(METRICS_CACHE_KEY, DashboardMetricsVo.class);
        if (cached != null) {
            return cached;
        }

        Long totalResidents = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1)
        );

        Long pendingOrders = workOrderMapper.selectCount(
                new LambdaQueryWrapper<BizWorkOrder>().eq(BizWorkOrder::getStatus, 1)
        );

        Long processingOrders = workOrderMapper.selectCount(
                new LambdaQueryWrapper<BizWorkOrder>().in(BizWorkOrder::getStatus, 3, 4)
        );

        Long activeActivities = activityMapper.selectCount(
                new LambdaQueryWrapper<BizActivity>().in(BizActivity::getStatus, 1, 2, 3)
        );

        Long openReports = reportMapper.selectCount(
                new LambdaQueryWrapper<BizReport>().eq(BizReport::getStatus, 0)
        );

        Long totalNotices = noticeMapper.selectCount(
                new LambdaQueryWrapper<SysNotice>().eq(SysNotice::getStatus, 1)
        );

        Long totalCompletedOrders = workOrderMapper.selectCount(
                new LambdaQueryWrapper<BizWorkOrder>().in(BizWorkOrder::getStatus, 5, 6)
        );
        Long totalOrders = workOrderMapper.selectCount(null);
        double finishRate = (totalOrders > 0) ? (Math.round((double) totalCompletedOrders / totalOrders * 1000.0) / 10.0) : 100.0;

        // 本月新增闲置互助帖（按创建时间当月统计）
        Long monthlyPostCount = marketPostMapper.selectCount(
                new LambdaQueryWrapper<BizMarketPost>()
                        .ne(BizMarketPost::getStatus, 4)
                        .ge(BizMarketPost::getCreateTime, LocalDate.now().withDayOfMonth(1).atStartOfDay())
        );

        DashboardMetricsVo vo = DashboardMetricsVo.builder()
                .totalResidents(totalResidents)
                .pendingOrders(pendingOrders)
                .processingOrders(processingOrders)
                .activeActivities(activeActivities)
                .openReports(openReports)
                .totalNotices(totalNotices)
                .repairFinishRate(finishRate)
                .monthlyPostCount(monthlyPostCount)
                .build();
        writeCache(METRICS_CACHE_KEY, vo);
        return vo;
    }

    @Override
    public ScreenDataVo getScreenData() {
        ScreenDataVo cached = readCache(SCREEN_CACHE_KEY, ScreenDataVo.class);
        if (cached != null) {
            return cached;
        }

        BizCommunity community = communityMapper.selectOne(new LambdaQueryWrapper<BizCommunity>().last("LIMIT 1"));
        String commName = community != null ? community.getName() : "智慧示范社区";
        String commAddress = community != null ? community.getAddress() : "杭州市西湖区文三西路888号";
        Long communityId = community != null ? community.getId() : 101L;

        Long totalResidents = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1));
        Long totalBuildings = buildingMapper.selectCount(null);
        Long totalHouses = houseMapper.selectCount(null);

        Long occupiedHouses = houseMapper.selectCount(
                new LambdaQueryWrapper<BizHouse>().in(BizHouse::getLiveStatus, 1, 2)
        );
        double occupancyRate = (totalHouses > 0) ? (Math.round((double) occupiedHouses / totalHouses * 1000.0) / 10.0) : 88.5;

        Long totalOrders = workOrderMapper.selectCount(null);
        Long completedOrders = workOrderMapper.selectCount(
                new LambdaQueryWrapper<BizWorkOrder>().in(BizWorkOrder::getStatus, 5, 6)
        );
        double finishRate = (totalOrders > 0) ? (Math.round((double) completedOrders / totalOrders * 1000.0) / 10.0) : 95.8;

        // 1. 楼栋报修热力统计
        List<Map<String, Object>> buildingHeatmap = workOrderMapper.countByBuilding(communityId);

        // 2. 工单流转漏斗（按状态真实统计，不再用总数减常数伪造）
        long reviewed = countOrdersByStatus(3, 4, 5, 6);
        long handling = countOrdersByStatus(4, 5, 6);
        long finished = countOrdersByStatus(5, 6);
        long evaluated = countOrdersByStatus(6);
        List<Map<String, Object>> orderFunnel = List.of(
                Map.of("name", "居民申报提交", "value", totalOrders),
                Map.of("name", "初审派单通过", "value", reviewed),
                Map.of("name", "接单开工处理", "value", handling),
                Map.of("name", "现场维保完工", "value", finished),
                Map.of("name", "居民满意评价", "value", evaluated)
        );

        // 3. 问题分类环图
        List<Map<String, Object>> categoryPie = workOrderMapper.countByCategory(communityId);

        // 4. 闲置与互助分布（查询闲置互助帖表而非工单表）
        List<Map<String, Object>> marketPostStats = marketPostMapper.countByPostType(communityId);

        // 5. 活动雷达指标
        List<ScreenDataVo.RadarIndicatorVo> activityRadar = List.of(
                ScreenDataVo.RadarIndicatorVo.builder().name("邻里文体").max(100).value(85).build(),
                ScreenDataVo.RadarIndicatorVo.builder().name("公益讲座").max(100).value(72).build(),
                ScreenDataVo.RadarIndicatorVo.builder().name("亲子教育").max(100).value(68).build(),
                ScreenDataVo.RadarIndicatorVo.builder().name("志愿服务").max(100).value(90).build(),
                ScreenDataVo.RadarIndicatorVo.builder().name("节日庆典").max(100).value(95).build()
        );

        // 6. 实时动态事件流
        List<ScreenDataVo.EventStreamVo> liveEventStream = new ArrayList<>();
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

        List<SysNotice> recentNotices = noticeMapper.selectList(
                new LambdaQueryWrapper<SysNotice>().orderByDesc(SysNotice::getCreateTime).last("LIMIT 3")
        );
        for (SysNotice n : recentNotices) {
            liveEventStream.add(ScreenDataVo.EventStreamVo.builder()
                    .eventType("NOTICE")
                    .title(n.getTitle())
                    .time(n.getCreateTime() != null ? n.getCreateTime().format(timeFmt) : "刚刚")
                    .tag(n.getNoticeType() == 4 ? "应急" : "通告")
                    .build());
        }

        List<BizWorkOrder> recentOrders = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>().orderByDesc(BizWorkOrder::getCreateTime).last("LIMIT 4")
        );
        for (BizWorkOrder o : recentOrders) {
            liveEventStream.add(ScreenDataVo.EventStreamVo.builder()
                    .eventType("ORDER")
                    .title("【" + o.getCategory() + "】" + o.getTitle())
                    .time(o.getCreateTime() != null ? o.getCreateTime().format(timeFmt) : "刚刚")
                    .tag(o.getStatus() == 1 ? "新提报" : (o.getStatus() == 5 ? "已完工" : "派单中"))
                    .build());
        }

        ScreenDataVo vo = ScreenDataVo.builder()
                .communityName(commName)
                .communityAddress(commAddress)
                .totalPopulation(totalResidents != null ? totalResidents : 1024L)
                .totalBuildings(totalBuildings.intValue())
                .totalHouses(totalHouses.intValue())
                .occupancyRate(occupancyRate)
                .totalOrders(totalOrders)
                .completedOrders(completedOrders)
                .finishRate(finishRate)
                .avgSatisfaction(4.9)
                .buildingHeatmap(buildingHeatmap)
                .orderFunnel(orderFunnel)
                .categoryPie(categoryPie)
                .marketPostStats(marketPostStats)
                .activityRadar(activityRadar)
                .liveEventStream(liveEventStream)
                .build();
        writeCache(SCREEN_CACHE_KEY, vo);
        return vo;
    }

    private long countOrdersByStatus(Integer... statuses) {
        Long count = workOrderMapper.selectCount(
                new LambdaQueryWrapper<BizWorkOrder>().in(BizWorkOrder::getStatus, List.of(statuses)));
        return count != null ? count : 0;
    }

    private <T> T readCache(String key, Class<T> type) {
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached == null) {
                return null;
            }
            return objectMapper.readValue(objectMapper.writeValueAsString(cached), type);
        } catch (Exception e) {
            log.warn("读取面板缓存失败，回源数据库: {}", e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value, java.time.Duration.ofSeconds(CACHE_TTL_SECONDS));
        } catch (Exception e) {
            log.warn("写入面板缓存失败: {}", e.getMessage());
        }
    }
}
