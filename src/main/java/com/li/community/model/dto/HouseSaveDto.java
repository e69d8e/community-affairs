package com.li.community.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class HouseSaveDto {

    /** 为空时视为新增，否则为更新 */
    private Long id;

    /** 新增时必填；更新时忽略（归属不允许变更） */
    private Long unitId;

    @NotBlank(message = "门牌号不能为空")
    @Pattern(regexp = "^[0-9A-Za-z\\-+]{1,16}(室)?$", message = "门牌号格式如 301 或 301室")
    private String houseNo;

    @NotNull(message = "所在楼层不能为空")
    @Min(value = -2, message = "楼层范围为地下 2 层至地上 80 层")
    @Max(value = 80, message = "楼层范围为地下 2 层至地上 80 层")
    private Integer floorNum;

    @Min(value = 1, message = "建筑面积需大于 0")
    @Max(value = 2000, message = "建筑面积不能超过 2000 ㎡")
    private BigDecimal area;

    @Size(max = 32, message = "户型描述长度不能超过 32 字符")
    private String houseType;

    @NotNull(message = "居住状态不能为空")
    @Min(1)
    @Max(3)
    private Integer liveStatus; // 1-业主自住, 2-房屋出租, 3-空置
}
