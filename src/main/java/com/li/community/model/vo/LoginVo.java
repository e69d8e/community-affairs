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
}
