package com.li.community.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.community.common.result.PageResult;
import com.li.community.model.entity.*;
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

    PageResult<BizResidentHouse> listPendingHouseBindings(Long page, Long size);
}
