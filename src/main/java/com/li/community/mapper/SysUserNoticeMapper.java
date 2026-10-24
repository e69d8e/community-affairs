package com.li.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.li.community.model.entity.SysUserNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserNoticeMapper extends BaseMapper<SysUserNotice> {

    @Select("SELECT COUNT(*) FROM sys_user_notice WHERE user_id = #{userId} AND is_read = 0")
    Long countUnread(@Param("userId") Long userId);
}
