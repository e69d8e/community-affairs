package com.li.community.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.community.common.result.PageResult;
import com.li.community.model.dto.BuildingSaveDto;
import com.li.community.model.dto.HouseBatchGenDto;
import com.li.community.model.dto.HouseSaveDto;
import com.li.community.model.dto.UnitSaveDto;
import com.li.community.model.entity.*;
import com.li.community.model.vo.HouseBindingVo;
import com.li.community.model.vo.UserVo;

import java.util.List;

public interface GridService {

    BizCommunity getCommunityInfo();

    List<BizBuilding> listBuildings(Long communityId);

    List<BizUnit> listUnits(Long buildingId);

    List<BizHouse> listHouses(Long unitId);

    List<UserVo.HouseInfoVo> getMyHouses();

    void submitHouseBinding(Long houseId, Integer identityType, String certMaterial);

    void auditHouseBinding(Long bindingId, Integer certStatus, String rejectReason);

    PageResult<HouseBindingVo> listPendingHouseBindings(Integer certStatus, Long communityId, Long page, Long size);

    // ==================== 楼宇空间结构维护（管理端） ====================

    /** 可选为楼栋长的网格员用户列表 (按小区过滤) */
    List<UserVo> listGridOfficers(Long communityId);

    /** 新增/修改楼栋（新增时可按单元数、楼层数自动生成单元与房屋台账） */
    BizBuilding saveBuilding(BuildingSaveDto dto);

    /** 删除楼栋（级联删除其下单元与房屋，存在居民认证绑定时拒绝） */
    void deleteBuilding(Long buildingId);

    /** 新增/修改单元 */
    BizUnit saveUnit(UnitSaveDto dto);

    /** 删除单元（级联删除其下房屋，存在居民认证绑定时拒绝） */
    void deleteUnit(Long unitId);

    /** 新增/修改房屋（新增时归属小区/楼栋由所属单元推导） */
    BizHouse saveHouse(HouseSaveDto dto);

    /** 按楼层区间批量生成房屋台账（如 3-18 层每层 4 户），跳过已存在门牌，返回实际生成数 */
    int batchGenHouses(HouseBatchGenDto dto);

    /** 删除房屋（存在居民认证绑定时拒绝） */
    void deleteHouse(Long houseId);
}
