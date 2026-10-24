package com.li.community.model.dto;

import lombok.Data;

@Data
public class UserUpdateDto {

    private String realName;
    private String phone;
    private String avatar;
    private Integer gender; // 0-女, 1-男, 2-保密
}
