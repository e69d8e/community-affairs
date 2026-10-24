package com.li.community.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CommunitySaveDto {

    /** 更新时必传; 创建时留空 */
    private Long id;

    @NotBlank(message = "小区名称不能为空")
    private String name;

    @NotBlank(message = "小区唯一编号不能为空")
    private String code;

    @NotBlank(message = "小区详细地址不能为空")
    private String address;

    private String province = "浙江省";
    private String city = "杭州市";
    private String district = "西湖区";
    private String contactPerson = "";
    private String contactPhone = "";
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String description;
    private Integer status = 1;
}
