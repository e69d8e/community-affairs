package com.li.community.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenDataVo implements Serializable {

    // 核心综述
    private String communityName;
    private String communityAddress;
    private Long totalPopulation;
    private Integer totalBuildings;
    private Integer totalHouses;
    private Double occupancyRate; // 入住率 (如 88.5%)

    // 核心业务指标
    private Long totalOrders;
    private Long completedOrders;
    private Double finishRate;
    private Double avgSatisfaction;

    // ECharts 图表数据组
    private List<Map<String, Object>> buildingHeatmap; // 楼栋热力排名
    private List<Map<String, Object>> orderFunnel;     // 工单流转漏斗
    private List<Map<String, Object>> categoryPie;     // 问题分类环图
    private List<Map<String, Object>> marketPostStats; // 闲置互助类型分布
    private List<RadarIndicatorVo> activityRadar;      // 活动参与维度雷达

    // 实时动态事件流
    private List<EventStreamVo> liveEventStream;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RadarIndicatorVo implements Serializable {
        private String name;
        private Integer max;
        private Integer value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventStreamVo implements Serializable {
        private String eventType; // NOTICE, ORDER, SIGNUP, REPORT
        private String title;
        private String time;
        private String tag;
    }
}
