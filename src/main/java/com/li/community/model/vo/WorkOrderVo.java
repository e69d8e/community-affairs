package com.li.community.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.li.community.model.entity.BizOrderLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderVo implements Serializable {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private String orderNo;
    private Integer orderType; // 1-报修, 2-投诉建议
    private String category;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long communityId;
    private String communityName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long buildingId;
    private String buildingName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long houseId;
    private String houseAddress; // 如: 1号楼1单元301室

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long reporterId;
    private String reporterName;
    private String reporterPhone;

    private String title;
    private String content;
    private String images;
    private Integer status;
    private Integer isUrgent;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long reviewerId;
    private String reviewerName;

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

    private Integer ratingStar;
    private String ratingContent;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private List<BizOrderLog> logs;
}
