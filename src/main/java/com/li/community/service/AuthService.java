package com.li.community.service;

import com.li.community.model.dto.AuthLoginDto;
import com.li.community.model.dto.AuthRegisterDto;
import com.li.community.model.entity.BizCommunity;
import com.li.community.model.vo.LoginVo;
import com.li.community.model.vo.UserVo;

import java.util.List;

public interface AuthService {

    LoginVo login(AuthLoginDto dto);

    LoginVo register(AuthRegisterDto dto);

    void logout();

    UserVo getCurrentUser();

    /**
     * 启用中的小区列表(注册选择/前端社区切换器共用, 公开接口)
     */
    List<BizCommunity> listActiveCommunities();
}
