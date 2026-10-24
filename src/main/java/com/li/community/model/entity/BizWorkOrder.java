package com.li.community.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("biz_work_order")
public class BizWorkOrder implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private String orderNo;
    private Integer orderType; // 1-报修, 2-投诉建议
    private String category;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long communityId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long buildingId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long houseId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long reporterId;

    private String reporterName;
    private String reporterPhone;
    private String title;
    private String content;
    private String images; // JSON 数组字符串

    /**
     * 1-待审核, 2-已驳回, 3-已派单待处理, 4-处理中, 5-已完工待评价, 6-已评价结单
     */
    private Integer status;
    private Integer isUrgent; // 0-普通, 1-紧急

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long reviewerId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;
    private String reviewRemark;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long handlerId;
    private String handlerName;
    private String handlerPhone;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handleTime;
    private String handleResult;
    private String handleImages;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;

    private Integer ratingStar; // 1-5星
    private String ratingContent;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
