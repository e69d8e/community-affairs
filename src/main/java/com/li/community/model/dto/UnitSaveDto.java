package com.li.community.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UnitSaveDto {

    /** 为空时视为新增，否则为更新 */
    private Long id;

    @NotNull(message = "所属楼栋不能为空")
    private Long buildingId;

    @NotBlank(message = "单元名称不能为空")
    @Size(max = 32, message = "单元名称长度不能超过 32 字符")
    private String unitName;

    @NotBlank(message = "单元编号不能为空")
    @Pattern(regexp = "^[0-9A-Za-z\\-]{1,20}$", message = "单元编号仅支持数字/字母/短横线")
    private String unitNo;

    @NotNull(message = "电梯配置不能为空")
    @Min(0)
    @Max(1)
    private Integer hasElevator; // 1-有电梯, 0-无电梯
}
