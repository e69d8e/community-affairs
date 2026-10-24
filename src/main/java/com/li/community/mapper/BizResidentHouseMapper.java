package com.li.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.li.community.model.entity.BizResidentHouse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface BizResidentHouseMapper extends BaseMapper<BizResidentHouse> {

    /**
     * 一次 JOIN 查出指定楼栋下所有认证绑定居民的用户 ID（替代楼栋→房屋→绑定三层循环查询）
     */
    @Select("""
            <script>
            SELECT DISTINCT rh.user_id
            FROM biz_resident_house rh
            JOIN biz_house h ON rh.house_id = h.id
            WHERE h.building_id IN
            <foreach collection="buildingIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Long> selectUserIdsByBuildingIds(@Param("buildingIds") Collection<Long> buildingIds);
}
