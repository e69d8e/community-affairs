package com.li.community.service;

import com.li.community.model.dto.AuthLoginDto;
import com.li.community.model.dto.AuthRegisterDto;
import com.li.community.model.vo.LoginVo;
import com.li.community.model.vo.UserVo;

public interface AuthService {

    LoginVo login(AuthLoginDto dto);

    LoginVo register(AuthRegisterDto dto);

    void logout();

    UserVo getCurrentUser();
}
