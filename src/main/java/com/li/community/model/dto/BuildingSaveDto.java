package com.li.community.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BuildingSaveDto {

    /** 为空时视为新增，否则为更新 */
    private Long id;

    /** 为空时默认取系统内唯一小区 */
    private Long communityId;

    @NotBlank(message = "楼栋名称不能为空")
    @Size(max = 64, message = "楼栋名称长度不能超过 64 字符")
    private String buildingName;

    @NotBlank(message = "楼栋编号不能为空")
    @Pattern(regexp = "^[0-9A-Za-z\\-]{1,20}$", message = "楼栋编号仅支持数字/字母/短横线")
    private String buildingNo;

    @NotNull(message = "单元数不能为空")
    @Min(value = 1, message = "单元数至少为 1")
    @Max(value = 30, message = "单元数不能超过 30")
    private Integer totalUnits;

    @NotNull(message = "楼层数不能为空")
    @Min(value = 1, message = "楼层数至少为 1")
    @Max(value = 80, message = "楼层数不能超过 80")
    private Integer totalFloors;

    /** 绑定的楼栋长/网格员，可为空 */
    private Long gridOfficerId;

    /** 仅新增生效：1-按单元数与楼层数自动生成单元及每层房屋 */
    private Integer autoGenerate;

    /** 仅新增且 autoGenerate=1 生效：每个楼层生成的户数（2-8） */
    @Min(value = 1, message = "每层户数至少为 1")
    @Max(value = 8, message = "每层户数不能超过 8")
    private Integer housesPerFloor;
}
