package com.li.community.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("biz_market_post")
public class BizMarketPost implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /**
     * 1-二手闲置, 2-邻里求助, 3-物品借用, 4-邻里拼车, 5-技能交换
     */
    private Integer postType;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long communityId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    private String title;
    private String description;
    private BigDecimal price;
    private Integer tradeType; // 1-一口价, 2-可面议, 3-免费赠送, 4-互助交换
    private Integer deliveryType; // 1-楼下自提, 2-上门送达, 3-服务台面交
    private String tradeAddress;
    private String contactType;
    private String contactValue;
    private String images; // JSON 数组

    /**
     * 1-展示中, 2-流转锁定中, 3-已达成结题, 4-违规封禁下架
     */
    private Integer status;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
