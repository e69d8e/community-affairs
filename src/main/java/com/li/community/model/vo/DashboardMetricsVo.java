package com.li.community.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMetricsVo implements Serializable {

    private Long totalResidents;     // 总住户数
    private Long pendingOrders;       // 待办/待审工单数
    private Long processingOrders;    // 处理中工单数
    private Long activeActivities;    // 报名进行中的活动数
    private Long openReports;         // 待核验举报数
    private Long totalNotices;        // 已发布公告通告数
    private Double repairFinishRate;  // 报修办结满意率 (如 98.5%)
    private Long monthlyPostCount;    // 本月邻里闲置互助活跃度
}
