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

    @Select("SELECT CASE post_type " +
            "WHEN 1 THEN '二手闲置' " +
            "WHEN 2 THEN '邻里求助' " +
            "WHEN 3 THEN '物品借用' " +
            "WHEN 4 THEN '邻里拼车' " +
            "WHEN 5 THEN '技能交换' " +
            "ELSE '其他互助' END AS name, " +
            "COUNT(*) AS value FROM biz_market_post " +
            "WHERE (#{communityId} IS NULL OR community_id = #{communityId}) " +
            "GROUP BY post_type ORDER BY value DESC")
    List<Map<String, Object>> countByPostType(@Param("communityId") Long communityId);
}
