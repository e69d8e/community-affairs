package com.li.community.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.community.common.exception.BizException;
import com.li.community.common.exception.ErrorCode;
import com.li.community.common.result.PageResult;
import com.li.community.common.utils.SecurityUtils;
import com.li.community.mapper.*;
import com.li.community.model.entity.*;
import com.li.community.model.vo.UserVo;
import com.li.community.service.GridService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GridServiceImpl implements GridService {

    private final BizCommunityMapper communityMapper;
    private final BizBuildingMapper buildingMapper;
    private final BizUnitMapper unitMapper;
    private final BizHouseMapper houseMapper;
    private final BizResidentHouseMapper residentHouseMapper;

    @Override
    public BizCommunity getCommunityInfo() {
        return communityMapper.selectOne(
                new LambdaQueryWrapper<BizCommunity>().last("LIMIT 1")
        );
    }

    @Override
    public List<BizBuilding> listBuildings(Long communityId) {
        return buildingMapper.selectList(
                new LambdaQueryWrapper<BizBuilding>()
                        .eq(communityId != null, BizBuilding::getCommunityId, communityId)
                        .orderByAsc(BizBuilding::getBuildingNo)
        );
    }

    @Override
    public List<BizUnit> listUnits(Long buildingId) {
        return unitMapper.selectList(
                new LambdaQueryWrapper<BizUnit>()
                        .eq(BizUnit::getBuildingId, buildingId)
                        .orderByAsc(BizUnit::getUnitNo)
        );
    }

    @Override
    public List<BizHouse> listHouses(Long unitId) {
        return houseMapper.selectList(
                new LambdaQueryWrapper<BizHouse>()
                        .eq(BizHouse::getUnitId, unitId)
                        .orderByAsc(BizHouse::getHouseNo)
        );
    }

    @Override
    public List<UserVo.HouseInfoVo> getMyHouses() {
        Long userId = SecurityUtils.getUserId();
        List<BizResidentHouse> bindings = residentHouseMapper.selectList(
                new LambdaQueryWrapper<BizResidentHouse>().eq(BizResidentHouse::getUserId, userId)
        );

        List<UserVo.HouseInfoVo> list = new ArrayList<>();
        for (BizResidentHouse b : bindings) {
            BizHouse house = houseMapper.selectById(b.getHouseId());
            if (house != null) {
                BizCommunity comm = communityMapper.selectById(house.getCommunityId());
                BizBuilding bld = buildingMapper.selectById(house.getBuildingId());
                BizUnit unit = unitMapper.selectById(house.getUnitId());

                list.add(UserVo.HouseInfoVo.builder()
                        .houseId(house.getId())
                        .communityName(comm != null ? comm.getName() : "")
                        .buildingName(bld != null ? bld.getBuildingName() : "")
                        .unitName(unit != null ? unit.getUnitName() : "")
                        .houseNo(house.getHouseNo())
                        .identityType(b.getIdentityType())
                        .certStatus(b.getCertStatus())
                        .build());
            }
        }
        return list;
    }

    @Override
    public void submitHouseBinding(Long houseId, Integer identityType, String certMaterial) {
        Long userId = SecurityUtils.getUserId();
        BizHouse house = houseMapper.selectById(houseId);
        if (house == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "房屋编号不存在");
        }

        // 检查是否已提交过绑定申请
        Long count = residentHouseMapper.selectCount(
                new LambdaQueryWrapper<BizResidentHouse>()
                        .eq(BizResidentHouse::getUserId, userId)
                        .eq(BizResidentHouse::getHouseId, houseId)
        );
        if (count > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "您已绑定或提交过该房屋的认证申请");
        }

        BizResidentHouse binding = BizResidentHouse.builder()
                .id(IdUtil.getSnowflakeNextId())
                .userId(userId)
                .houseId(houseId)
                .identityType(identityType != null ? identityType : 1)
                .certStatus(0) // 待审核
                .certMaterial(certMaterial)
                .build();

        residentHouseMapper.insert(binding);
    }

    @Override
    public void auditHouseBinding(Long bindingId, Integer certStatus, String rejectReason) {
        Long reviewerId = SecurityUtils.getUserId();
        BizResidentHouse binding = residentHouseMapper.selectById(bindingId);
        if (binding == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "绑定记录不存在");
        }

        binding.setCertStatus(certStatus);
        binding.setRejectReason(rejectReason != null ? rejectReason : "");
        binding.setReviewUserId(reviewerId);
        binding.setReviewTime(LocalDateTime.now());

        residentHouseMapper.updateById(binding);
    }

    @Override
    public PageResult<BizResidentHouse> listPendingHouseBindings(Long page, Long size) {
        Page<BizResidentHouse> pageParam = new Page<>(page != null ? page : 1, size != null ? size : 10);
        Page<BizResidentHouse> result = residentHouseMapper.selectPage(
                pageParam,
                new LambdaQueryWrapper<BizResidentHouse>()
                        .orderByDesc(BizResidentHouse::getCreateTime)
        );
        return PageResult.of(result);
    }
}
