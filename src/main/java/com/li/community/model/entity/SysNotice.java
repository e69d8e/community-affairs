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
@TableName("sys_notice")
public class SysNotice implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private String title;
    private Integer noticeType; // 1-政策宣传, 2-便民提示, 3-物业通知, 4-突发应急广播

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long communityId;

    private Integer targetScope; // 1-全区广播, 2-指定楼栋定向
    private String targetBuildingIds; // JSON 数组如 [201, 202]
    private Integer isTop; // 1-置顶, 0-常规
    private String content; // 富文本 HTML

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long publisherId;
    private String publisherName;
    private Integer viewCount;
    private Integer status; // 0-草稿, 1-正式发布, 2-已撤回

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
