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
@TableName("biz_report")
public class BizReport implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private String targetType; // POST, COMMENT, USER

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long targetId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long reporterId;

    private String reasonCategory;
    private String description;
    private String evidenceImages;
    private Integer status; // 0-待核查, 1-确认违规下架, 2-驳回举报

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long handlerId;
    private String handleRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handleTime;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
