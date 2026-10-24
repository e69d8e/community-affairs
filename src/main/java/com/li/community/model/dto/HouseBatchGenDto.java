package com.li.community.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HouseBatchGenDto {

    @NotNull(message = "所属单元不能为空")
    private Long unitId;

    @NotNull(message = "起始楼层不能为空")
    @Min(1)
    @Max(80)
    private Integer startFloor;

    @NotNull(message = "结束楼层不能为空")
    @Min(1)
    @Max(80)
    private Integer endFloor;

    @NotNull(message = "每层户数不能为空")
    @Min(value = 1, message = "每层户数至少为 1")
    @Max(value = 8, message = "每层户数不能超过 8")
    private Integer housesPerFloor;

    /**
     * 门牌基准样式：如 3 层每层 4 户生成 301、302、303、304。
     * 已存在的门牌号自动跳过，不重复生成。
     */
}
