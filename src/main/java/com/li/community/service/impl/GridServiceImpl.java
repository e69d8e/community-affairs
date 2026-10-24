package com.li.community.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.community.common.constant.RoleConstant;
import com.li.community.common.exception.BizException;
import com.li.community.common.exception.ErrorCode;
import com.li.community.common.result.PageResult;
import com.li.community.common.security.CommunityScopeResolver;
import com.li.community.common.utils.SecurityUtils;
import com.li.community.mapper.*;
import com.li.community.model.dto.BuildingSaveDto;
import com.li.community.model.dto.HouseBatchGenDto;
import com.li.community.model.dto.HouseSaveDto;
import com.li.community.model.dto.UnitSaveDto;
import com.li.community.model.entity.*;
import com.li.community.model.vo.UserVo;
import com.li.community.service.GridService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GridServiceImpl implements GridService {

    private final BizCommunityMapper communityMapper;
    private final BizBuildingMapper buildingMapper;
    private final BizUnitMapper unitMapper;
    private final BizHouseMapper houseMapper;
    private final BizResidentHouseMapper residentHouseMapper;
    private final SysUserMapper sysUserMapper;
    private final CommunityScopeResolver scopeResolver;

    @Override
    public BizCommunity getCommunityInfo() {
        // 当前用户所属小区; 平台管理员(未归属小区)回退第一个启用小区
        Long own = SecurityUtils.getCommunityId();
        if (own != null) {
            return communityMapper.selectById(own);
        }
        return communityMapper.selectOne(
                new LambdaQueryWrapper<BizCommunity>()
                        .eq(BizCommunity::getStatus, 1)
                        .orderByAsc(BizCommunity::getId)
                        .last("LIMIT 1")
        );
    }

    @Override
    public List<BizBuilding> listBuildings(Long communityId) {
        // 非平台管理员强制本人小区楼栋; 平台管理员可指定小区, 未指定返回全部
        Long communityScope = scopeResolver.resolveAdminScope(communityId);
        return buildingMapper.selectList(
                new LambdaQueryWrapper<BizBuilding>()
                        .eq(communityScope != null, BizBuilding::getCommunityId, communityScope)
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
        // 只能绑定本人所属小区的房屋, 防止跨小区认证
        checkCommunityScope(house.getCommunityId());

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
        // 网格员数据范围：仅可审批本人负责楼栋下房屋的绑定申请
        if (SecurityUtils.hasRole(RoleConstant.ROLE_GRID_OFFICER)
                && !currentOfficerHouseIds().contains(binding.getHouseId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "该房屋不在您负责的楼栋范围内，无法审批");
        }
        // 社区数据范围: 非平台管理员仅可审批本人小区的绑定申请
        BizHouse boundHouse = houseMapper.selectById(binding.getHouseId());
        if (boundHouse != null) {
            checkCommunityScope(boundHouse.getCommunityId());
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

        LambdaQueryWrapper<BizResidentHouse> wrapper = new LambdaQueryWrapper<BizResidentHouse>()
                .orderByDesc(BizResidentHouse::getCreateTime);

        // 网格员数据范围：仅可见本人负责楼栋下房屋的绑定申请
        if (SecurityUtils.hasRole(RoleConstant.ROLE_GRID_OFFICER)) {
            List<Long> houseIds = currentOfficerHouseIds();
            if (houseIds.isEmpty()) {
                return PageResult.of(new ArrayList<>(), 0L, pageParam.getSize(), pageParam.getCurrent());
            }
            wrapper.in(BizResidentHouse::getHouseId, houseIds);
        } else {
            // 其他管理角色: 非平台管理员仅可见本人小区房屋的绑定申请
            Long communityScope = scopeResolver.resolveAdminScope(null);
            if (communityScope != null) {
                List<Long> communityHouseIds = houseMapper.selectList(
                                new LambdaQueryWrapper<BizHouse>()
                                        .select(BizHouse::getId)
                                        .eq(BizHouse::getCommunityId, communityScope))
                        .stream().map(BizHouse::getId).collect(Collectors.toList());
                if (communityHouseIds.isEmpty()) {
                    return PageResult.of(new ArrayList<>(), 0L, pageParam.getSize(), pageParam.getCurrent());
                }
                wrapper.in(BizResidentHouse::getHouseId, communityHouseIds);
            }
        }

        Page<BizResidentHouse> result = residentHouseMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 当前登录网格员负责楼栋(biz_building.grid_officer_id)下的全部房屋 ID。
     */
    private List<Long> currentOfficerHouseIds() {
        List<Long> buildingIds = buildingMapper.selectList(
                        new LambdaQueryWrapper<BizBuilding>()
                                .select(BizBuilding::getId)
                                .eq(BizBuilding::getGridOfficerId, SecurityUtils.getUserId()))
                .stream().map(BizBuilding::getId).collect(Collectors.toList());
        if (buildingIds.isEmpty()) {
            return new ArrayList<>();
        }
        return houseMapper.selectList(
                        new LambdaQueryWrapper<BizHouse>()
                                .select(BizHouse::getId)
                                .in(BizHouse::getBuildingId, buildingIds))
                .stream().map(BizHouse::getId).collect(Collectors.toList());
    }

    // ==================== 楼宇空间结构维护（管理端） ====================

    @Override
    public List<UserVo> listGridOfficers(Long communityId) {
        Long communityScope = scopeResolver.resolveAdminScope(communityId);
        return sysUserMapper.selectList(
                        new LambdaQueryWrapper<SysUser>()
                                .select(SysUser::getId, SysUser::getRealName, SysUser::getPhone, SysUser::getRole)
                                .eq(SysUser::getRole, RoleConstant.ROLE_GRID_OFFICER)
                                .eq(SysUser::getStatus, 1)
                                .eq(communityScope != null, SysUser::getCommunityId, communityScope))
                .stream()
                .map(u -> UserVo.builder()
                        .id(u.getId())
                        .realName(u.getRealName())
                        .phone(u.getPhone())
                        .role(u.getRole())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizBuilding saveBuilding(BuildingSaveDto dto) {
        // 创建: 平台管理员必须指定小区, 其余角色强制本人小区
        Long communityId = scopeResolver.resolveForCreate(dto.getCommunityId());

        if (dto.getId() == null) {
            checkBuildingNoUnique(communityId, dto.getBuildingNo(), null);
            BizBuilding building = BizBuilding.builder()
                    .communityId(communityId)
                    .buildingName(dto.getBuildingName())
                    .buildingNo(dto.getBuildingNo())
                    .totalUnits(dto.getTotalUnits())
                    .totalFloors(dto.getTotalFloors())
                    .gridOfficerId(dto.getGridOfficerId())
                    .build();
            validateGridOfficer(building.getGridOfficerId(), communityId);
            buildingMapper.insert(building);

            if (dto.getAutoGenerate() != null && dto.getAutoGenerate() == 1) {
                int housesPerFloor = dto.getHousesPerFloor() != null ? dto.getHousesPerFloor() : 2;
                generateUnitsAndHouses(building, housesPerFloor);
            }
            return building;
        }

        BizBuilding existing = requireBuilding(dto.getId());
        // 非平台管理员只能维护本人小区的楼栋, 且不允许把楼栋迁移到其他小区
        checkCommunityScope(existing.getCommunityId());
        if (!SecurityUtils.isSystemAdmin() && !existing.getCommunityId().equals(communityId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "不允许变更楼栋所属小区");
        }
        // 无论是否跨小区，均按目标小区校验编号唯一（排除自身）
        checkBuildingNoUnique(communityId, dto.getBuildingNo(), dto.getId());
        validateGridOfficer(dto.getGridOfficerId(), communityId);
        // 用 UpdateWrapper 显式 set，保证 gridOfficerId 传空时可解绑楼栋长
        buildingMapper.update(null, new LambdaUpdateWrapper<BizBuilding>()
                .eq(BizBuilding::getId, dto.getId())
                .set(BizBuilding::getCommunityId, communityId)
                .set(BizBuilding::getBuildingName, dto.getBuildingName())
                .set(BizBuilding::getBuildingNo, dto.getBuildingNo())
                .set(BizBuilding::getTotalUnits, dto.getTotalUnits())
                .set(BizBuilding::getTotalFloors, dto.getTotalFloors())
                .set(BizBuilding::getGridOfficerId, dto.getGridOfficerId()));
        return requireBuilding(dto.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBuilding(Long buildingId) {
        BizBuilding building = requireBuilding(buildingId);
        checkCommunityScope(building.getCommunityId());
        List<Long> unitIds = unitMapper.selectList(
                        new LambdaQueryWrapper<BizUnit>().select(BizUnit::getId)
                                .eq(BizUnit::getBuildingId, buildingId))
                .stream().map(BizUnit::getId).collect(Collectors.toList());

        rejectIfHousesBound(houseIdsOfUnits(unitIds), "楼栋下存在居民认证绑定的房屋，无法删除");

        if (!unitIds.isEmpty()) {
            houseMapper.delete(new LambdaQueryWrapper<BizHouse>().in(BizHouse::getUnitId, unitIds));
            unitMapper.deleteByIds(unitIds);
        }
        buildingMapper.deleteById(buildingId);
    }

    @Override
    public BizUnit saveUnit(UnitSaveDto dto) {
        BizBuilding targetBuilding = requireBuilding(dto.getBuildingId());
        checkCommunityScope(targetBuilding.getCommunityId());
        Long duplicate = unitMapper.selectCount(
                new LambdaQueryWrapper<BizUnit>()
                        .eq(BizUnit::getBuildingId, dto.getBuildingId())
                        .eq(BizUnit::getUnitNo, dto.getUnitNo())
                        .ne(dto.getId() != null, BizUnit::getId, dto.getId()));
        if (duplicate > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该楼栋下已存在编号为 " + dto.getUnitNo() + " 的单元");
        }

        if (dto.getId() == null) {
            BizUnit unit = BizUnit.builder()
                    .buildingId(dto.getBuildingId())
                    .unitName(dto.getUnitName())
                    .unitNo(dto.getUnitNo())
                    .hasElevator(dto.getHasElevator())
                    .build();
            unitMapper.insert(unit);
            return unit;
        }

        BizUnit db = unitMapper.selectById(dto.getId());
        if (db == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "单元不存在或已被删除");
        }
        // 归属楼栋不允许变更，避免台账与房屋挂载关系错乱
        if (!db.getBuildingId().equals(dto.getBuildingId())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "单元不允许变更所属楼栋");
        }
        unitMapper.update(null, new LambdaUpdateWrapper<BizUnit>()
                .eq(BizUnit::getId, dto.getId())
                .set(BizUnit::getUnitName, dto.getUnitName())
                .set(BizUnit::getUnitNo, dto.getUnitNo())
                .set(BizUnit::getHasElevator, dto.getHasElevator()));
        return unitMapper.selectById(dto.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUnit(Long unitId) {
        BizUnit unit = unitMapper.selectById(unitId);
        if (unit == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "单元不存在或已被删除");
        }
        checkCommunityScope(requireBuilding(unit.getBuildingId()).getCommunityId());
        rejectIfHousesBound(houseIdsOfUnits(List.of(unitId)), "单元下存在居民认证绑定的房屋，无法删除");

        houseMapper.delete(new LambdaQueryWrapper<BizHouse>().eq(BizHouse::getUnitId, unitId));
        unitMapper.deleteById(unitId);
    }

    @Override
    public BizHouse saveHouse(HouseSaveDto dto) {
        if (dto.getId() == null) {
            if (dto.getUnitId() == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "新增房屋必须指定所属单元");
            }
            BizUnit unit = requireUnit(dto.getUnitId());
            BizBuilding building = requireBuilding(unit.getBuildingId());
            checkCommunityScope(building.getCommunityId());
            Long duplicate = houseMapper.selectCount(
                    new LambdaQueryWrapper<BizHouse>()
                            .eq(BizHouse::getUnitId, unit.getId())
                            .eq(BizHouse::getHouseNo, dto.getHouseNo()));
            if (duplicate > 0) {
                throw new BizException(ErrorCode.BAD_REQUEST, "该单元下已存在门牌 " + dto.getHouseNo());
            }

            BizHouse house = BizHouse.builder()
                    .communityId(building.getCommunityId())
                    .buildingId(building.getId())
                    .unitId(unit.getId())
                    .houseNo(dto.getHouseNo())
                    .floorNum(dto.getFloorNum())
                    .area(dto.getArea() != null ? dto.getArea() : new BigDecimal("98.50"))
                    .houseType(dto.getHouseType() != null && !dto.getHouseType().isBlank() ? dto.getHouseType() : "三室两厅")
                    .liveStatus(dto.getLiveStatus())
                    .build();
            houseMapper.insert(house);
            return house;
        }

        BizHouse db = houseMapper.selectById(dto.getId());
        if (db == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "房屋不存在或已被删除");
        }
        checkCommunityScope(db.getCommunityId());
        Long duplicate = houseMapper.selectCount(
                new LambdaQueryWrapper<BizHouse>()
                        .eq(BizHouse::getUnitId, db.getUnitId())
                        .eq(BizHouse::getHouseNo, dto.getHouseNo())
                        .ne(BizHouse::getId, dto.getId()));
        if (duplicate > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该单元下已存在门牌 " + dto.getHouseNo());
        }
        // 显式 set 保证 area/houseType 可被清空回写
        houseMapper.update(null, new LambdaUpdateWrapper<BizHouse>()
                .eq(BizHouse::getId, dto.getId())
                .set(BizHouse::getHouseNo, dto.getHouseNo())
                .set(BizHouse::getFloorNum, dto.getFloorNum())
                .set(BizHouse::getArea, dto.getArea())
                .set(BizHouse::getHouseType, dto.getHouseType())
                .set(BizHouse::getLiveStatus, dto.getLiveStatus()));
        return houseMapper.selectById(dto.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchGenHouses(HouseBatchGenDto dto) {
        if (dto.getEndFloor() < dto.getStartFloor()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "结束楼层不能小于起始楼层");
        }
        BizUnit unit = requireUnit(dto.getUnitId());
        BizBuilding building = requireBuilding(unit.getBuildingId());
        checkCommunityScope(building.getCommunityId());

        Set<String> existingNos = houseMapper.selectList(
                        new LambdaQueryWrapper<BizHouse>().select(BizHouse::getHouseNo)
                                .eq(BizHouse::getUnitId, unit.getId()))
                .stream().map(BizHouse::getHouseNo).collect(Collectors.toSet());

        int generated = 0;
        for (int floor = dto.getStartFloor(); floor <= dto.getEndFloor(); floor++) {
            for (int seq = 1; seq <= dto.getHousesPerFloor(); seq++) {
                String houseNo = floor + String.format("%02d", seq) + "室";
                if (existingNos.contains(houseNo)) {
                    continue;
                }
                houseMapper.insert(BizHouse.builder()
                        .communityId(building.getCommunityId())
                        .buildingId(building.getId())
                        .unitId(unit.getId())
                        .houseNo(houseNo)
                        .floorNum(floor)
                        .area(new BigDecimal("98.50"))
                        .houseType("三室两厅")
                        .liveStatus(3) // 默认空置，待入户登记后调整
                        .build());
                generated++;
            }
        }
        return generated;
    }

    @Override
    public void deleteHouse(Long houseId) {
        BizHouse house = houseMapper.selectById(houseId);
        if (house == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "房屋不存在或已被删除");
        }
        checkCommunityScope(house.getCommunityId());
        rejectIfHousesBound(List.of(houseId), "该房屋存在居民认证绑定记录，请先解除绑定后再删除");
        houseMapper.deleteById(houseId);
    }

    // ==================== 私有辅助 ====================

    /**
     * 社区越权校验: 非平台管理员只能维护本人所属小区的空间台账
     */
    private void checkCommunityScope(Long communityId) {
        if (!scopeResolver.isInScope(communityId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权维护其他小区的空间台账");
        }
    }

    private BizBuilding requireBuilding(Long buildingId) {
        BizBuilding building = buildingMapper.selectById(buildingId);
        if (building == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "楼栋不存在或已被删除");
        }
        return building;
    }

    private BizUnit requireUnit(Long unitId) {
        BizUnit unit = unitMapper.selectById(unitId);
        if (unit == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "单元不存在或已被删除");
        }
        return unit;
    }

    private void checkBuildingNoUnique(Long communityId, String buildingNo, Long excludeId) {
        Long duplicate = buildingMapper.selectCount(
                new LambdaQueryWrapper<BizBuilding>()
                        .eq(BizBuilding::getCommunityId, communityId)
                        .eq(BizBuilding::getBuildingNo, buildingNo)
                        .ne(excludeId != null, BizBuilding::getId, excludeId));
        if (duplicate > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "当前小区已存在编号为 " + buildingNo + " 的楼栋");
        }
    }

    private void validateGridOfficer(Long gridOfficerId, Long communityId) {
        if (gridOfficerId == null) {
            return;
        }
        SysUser officer = sysUserMapper.selectById(gridOfficerId);
        if (officer == null || !RoleConstant.ROLE_GRID_OFFICER.equals(officer.getRole())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "所选楼栋长不是有效的网格员账号");
        }
        // 网格员只能绑定本人所属小区的楼栋
        if (communityId != null && officer.getCommunityId() != null
                && !communityId.equals(officer.getCommunityId())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "所选网格员不属于本小区，无法绑定楼栋");
        }
    }

    /** 新增楼栋后按 单元数 × 楼层数 × 每层户数 生成单元与房屋台账 */
    private void generateUnitsAndHouses(BizBuilding building, int housesPerFloor) {
        for (int i = 1; i <= building.getTotalUnits(); i++) {
            BizUnit unit = BizUnit.builder()
                    .buildingId(building.getId())
                    .unitName(i + "单元")
                    .unitNo(String.valueOf(i))
                    .hasElevator(1)
                    .build();
            unitMapper.insert(unit);

            for (int floor = 1; floor <= building.getTotalFloors(); floor++) {
                for (int seq = 1; seq <= housesPerFloor; seq++) {
                    houseMapper.insert(BizHouse.builder()
                            .communityId(building.getCommunityId())
                            .buildingId(building.getId())
                            .unitId(unit.getId())
                            .houseNo(floor + String.format("%02d", seq) + "室")
                            .floorNum(floor)
                            .area(new BigDecimal("98.50"))
                            .houseType("三室两厅")
                            .liveStatus(3)
                            .build());
                }
            }
        }
    }

    private List<Long> houseIdsOfUnits(List<Long> unitIds) {
        if (unitIds.isEmpty()) {
            return new ArrayList<>();
        }
        return houseMapper.selectList(
                        new LambdaQueryWrapper<BizHouse>().select(BizHouse::getId)
                                .in(BizHouse::getUnitId, unitIds))
                .stream().map(BizHouse::getId).collect(Collectors.toList());
    }

    private void rejectIfHousesBound(List<Long> houseIds, String message) {
        if (houseIds.isEmpty()) {
            return;
        }
        Long bound = residentHouseMapper.selectCount(
                new LambdaQueryWrapper<BizResidentHouse>().in(BizResidentHouse::getHouseId, houseIds));
        if (bound > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, message);
        }
    }
}
