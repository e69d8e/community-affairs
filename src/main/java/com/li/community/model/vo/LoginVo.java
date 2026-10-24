package com.li.community.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVo implements Serializable {

    private String token;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private String username;
    private String realName;
    private String phone;
    private String avatar;
    private Integer gender;
    private String role;
    private Integer creditScore;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long communityId; // 所属小区ID; NULL=平台级账号

    private String communityName; // 所属小区名称(平台级账号为空)
}
