package com.li.community.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MarketPostCreateDto {

    @NotNull(message = "帖子类型不能为空")
    private Integer postType; // 1-二手闲置, 2-求助, 3-借用, 4-拼车, 5-技能交换

    @NotBlank(message = "标题不能为空")
    private String title;

    @NotBlank(message = "详细介绍不能为空")
    private String description;

    private BigDecimal price; // 默认为0
    private Integer tradeType; // 1-一口价, 2-可面议, 3-免费赠送, 4-互助交换
    private Integer deliveryType; // 1-自提, 2-上门送达, 3-指定地点
    private String tradeAddress;
    private String contactType;
    private String contactValue;
    private String images; // JSON 图片 URL 数组
}
