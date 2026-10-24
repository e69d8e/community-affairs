package com.li.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.li.community.model.entity.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {}
