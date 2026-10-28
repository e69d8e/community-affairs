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
@Schema(description = "AI 智能结构化报修解析出参")
public class AiOrderParseVo {

    @Schema(description = "AI 规范化提取的标准工单标题", example = "阳台顶棚给排水管道爆裂漏水")
    private String title;

    @Schema(description = "AI 识别推荐的报修分类 (给排水系统/强电弱电/电梯公用/门禁安防/公用设施/环境卫生)", example = "给排水系统")
    private String category;

    @Schema(description = "AI 预测的紧急程度：0-普通, 1-紧急", example = "1")
    private Integer isUrgent;

    @Schema(description = "AI 判定紧急程度的专业依据", example = "检测到管道爆裂持续喷水，且临近强电插座，存在漏电及浸泡次生危害，定级为紧急。")
    private String urgentReason;

    @Schema(description = "AI 整理后的规范化工单正文描述", example = "居民户内阳台顶棚给排水管爆裂，发生持续漏水，现场有电器插座面临浸水风险，亟需专业维修师傅携带管钳及备用管件紧急排查抢修。")
    private String standardContent;
}
