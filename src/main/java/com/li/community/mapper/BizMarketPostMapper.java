package com.li.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.li.community.model.entity.BizMarketPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface BizMarketPostMapper extends BaseMapper<BizMarketPost> {

    @Select("SELECT post_type AS name, COUNT(*) AS value FROM biz_market_post " +
            "WHERE community_id = #{communityId} GROUP BY post_type")
    List<Map<String, Object>> countByPostType(@Param("communityId") Long communityId);
}
