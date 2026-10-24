package com.li.community.service;

import com.li.community.common.result.PageResult;
import com.li.community.model.dto.UserUpdateDto;
import com.li.community.model.entity.SysUser;
import com.li.community.model.vo.UserVo;

public interface UserService {

    void updateProfile(UserUpdateDto dto);

    void updatePassword(String oldPassword, String newPassword);

    PageResult<UserVo> listUsers(String keyword, String role, Long page, Long size);

    void updateUserStatus(Long userId, Integer status);

    void updateUserRole(Long userId, String role);
}
