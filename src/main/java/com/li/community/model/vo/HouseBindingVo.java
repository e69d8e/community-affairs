package com.li.community.model.vo;

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
public class HouseBindingVo implements Serializable {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    private String residentName;
    private String residentPhone;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long houseId;

    private String communityName;
    private String buildingName;
    private String unitName;
    private String houseNo;
    private String houseAddress;

    private Integer identityType; // 1-业主, 2-家属, 3-租客
    private Integer certStatus;   // 0-待审核, 1-已认证, 2-已驳回
    private String certMaterial;
    private String rejectReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
