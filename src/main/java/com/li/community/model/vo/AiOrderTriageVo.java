package com.li.community.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理端工单 AI 智能分诊研判出参")
public class AiOrderTriageVo {

    @Schema(description = "故障成因初步诊断分析", example = "初步判断为高层管道水锤效应或接头密封生料带老化导致法兰开裂。")
    private String faultDiagnosis;

    @Schema(description = "现场排查与维修要点建议", example = "1. 到场前提示业主关闭户内总进水阀；2. 携带PPR热熔机、25mm管件、防电手套；3. 排查楼下邻居顶棚是否有渗漏迹象。")
    private String maintenanceTips;

    @Schema(description = "推荐派工工种/角色", example = "综合维修工(水电水暖专长)")
    private String recommendedRole;

    @Schema(description = "预估维修处置工期(小时)", example = "1.5")
    private Double estimatedHours;

    @Schema(description = "AI 生成的标准审核与派单批注(可一键填入审核框)", example = "已通过初审。属紧急给排水故障，已推荐派发水暖专项师傅，请备好应急接水工具迅速上门排险。")
    private String suggestedAuditRemark;
}
