package com.li.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.li.community.model.entity.BizWorkOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface BizWorkOrderMapper extends BaseMapper<BizWorkOrder> {

    @Select("SELECT category AS name, COUNT(*) AS value FROM biz_work_order " +
            "WHERE community_id = #{communityId} GROUP BY category")
    List<Map<String, Object>> countByCategory(@Param("communityId") Long communityId);

    @Select("SELECT status AS name, COUNT(*) AS value FROM biz_work_order " +
            "WHERE community_id = #{communityId} GROUP BY status")
    List<Map<String, Object>> countByStatus(@Param("communityId") Long communityId);

    @Select("SELECT b.building_name AS name, COUNT(w.id) AS value " +
            "FROM biz_building b " +
            "LEFT JOIN biz_work_order w ON b.id = w.building_id " +
            "WHERE b.community_id = #{communityId} " +
            "GROUP BY b.id, b.building_name ORDER BY value DESC")
    List<Map<String, Object>> countByBuilding(@Param("communityId") Long communityId);
}
