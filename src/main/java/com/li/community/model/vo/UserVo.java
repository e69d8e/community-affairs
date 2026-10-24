package com.li.community.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVo implements Serializable {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private String username;
    private String realName;
    private String phone;
    private String avatar;
    private Integer gender;
    private String role;
    private Integer status;
    private Integer creditScore;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long communityId; // 所属小区ID; NULL=平台级账号

    private String communityName; // 所属小区名称(平台级账号为空)

    private List<HouseInfoVo> boundHouses;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HouseInfoVo implements Serializable {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long houseId;
        private String communityName;
        private String buildingName;
        private String unitName;
        private String houseNo;
        private Integer identityType; // 1-业主, 2-家属, 3-租客
        private Integer certStatus;   // 0-待审, 1-已认证, 2-驳回
    }
}
