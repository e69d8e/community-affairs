package com.li.community.controller;

import com.li.community.common.annotation.LogOperation;
import com.li.community.common.result.PageResult;
import com.li.community.common.result.Result;
import com.li.community.model.dto.BuildingSaveDto;
import com.li.community.model.dto.HouseBatchGenDto;
import com.li.community.model.dto.HouseSaveDto;
import com.li.community.model.dto.UnitSaveDto;
import com.li.community.model.entity.*;
import com.li.community.model.vo.HouseBindingVo;
import com.li.community.model.vo.UserVo;
import com.li.community.service.GridService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "02. 社区网格空间模块")
@RestController
@RequestMapping("/api/grid")
@RequiredArgsConstructor
public class GridController {

    private final GridService gridService;

    @Operation(summary = "获取当前小区基本概况")
    @GetMapping("/community/current")
    public Result<BizCommunity> getCommunityInfo() {
        return Result.success(gridService.getCommunityInfo());
    }

    @Operation(summary = "获取楼栋列表")
    @GetMapping("/buildings")
    public Result<List<BizBuilding>> listBuildings(@RequestParam(required = false) Long communityId) {
        return Result.success(gridService.listBuildings(communityId));
    }

    @Operation(summary = "获取指定楼栋下的单元列表")
    @GetMapping("/units/{buildingId}")
    public Result<List<BizUnit>> listUnits(@PathVariable Long buildingId) {
        return Result.success(gridService.listUnits(buildingId));
    }

    @Operation(summary = "获取指定单元下的房屋列表")
    @GetMapping("/houses/{unitId}")
    public Result<List<BizHouse>> listHouses(@PathVariable Long unitId) {
        return Result.success(gridService.listHouses(unitId));
    }

    @Operation(summary = "居民获取本人已绑定的房产列表")
    @GetMapping("/my-houses")
    public Result<List<UserVo.HouseInfoVo>> getMyHouses() {
        return Result.success(gridService.getMyHouses());
    }

    @Operation(summary = "居民申请认证绑定房屋")
    @PostMapping("/house-bind")
    public Result<Void> submitHouseBinding(@RequestBody Map<String, Object> params) {
        Long houseId = Long.parseLong(params.get("houseId").toString());
        Integer identityType = params.get("identityType") != null ? Integer.parseInt(params.get("identityType").toString()) : 1;
        String certMaterial = params.get("certMaterial") != null ? params.get("certMaterial").toString() : "";
        gridService.submitHouseBinding(houseId, identityType, certMaterial);
        return Result.success("房屋认证申请已提交，请等待网格员或物业审核", null);
    }

    @Operation(summary = "管理端获取房屋认证绑定列表 (默认按条件过滤，网格员仅可见本人负责楼栋)")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'PROPERTY_ADMIN', 'GRID_OFFICER')")
    @GetMapping("/admin/house-bind/list")
    public Result<PageResult<HouseBindingVo>> listPendingHouseBindings(
            @RequestParam(required = false) Integer certStatus,
            @RequestParam(required = false) Long communityId,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(gridService.listPendingHouseBindings(certStatus, communityId, page, size));
    }

    @Operation(summary = "网格员/管理员审批房屋绑定申请")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'PROPERTY_ADMIN', 'GRID_OFFICER')")
    @PutMapping("/admin/house-bind/audit")
    public Result<Void> auditHouseBinding(@RequestBody Map<String, Object> params) {
        Long bindingId = Long.parseLong(params.get("bindingId").toString());
        Integer certStatus = Integer.parseInt(params.get("certStatus").toString());
        String rejectReason = params.get("rejectReason") != null ? params.get("rejectReason").toString() : "";
        gridService.auditHouseBinding(bindingId, certStatus, rejectReason);
        return Result.success("审核处理完成", null);
    }

    // ==================== 楼宇空间结构维护（系统/社区/物业管理员） ====================

    @Operation(summary = "可选楼栋长(网格员)账号列表,用于楼栋绑定下拉 (非平台管理员仅本人小区网格员)")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'PROPERTY_ADMIN')")
    @GetMapping("/admin/officers")
    public Result<List<UserVo>> listGridOfficers(@RequestParam(required = false) Long communityId) {
        return Result.success(gridService.listGridOfficers(communityId));
    }

    @Operation(summary = "新增楼栋 (可选按单元数/楼层数自动生成单元与每层房屋台账)")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'PROPERTY_ADMIN')")
    @PostMapping("/admin/building")
    @LogOperation(title = "新增楼栋台账", businessType = "INSERT", saveParam = true)
    public Result<BizBuilding> createBuilding(@Valid @RequestBody BuildingSaveDto dto) {
        return Result.success("楼栋台账已创建", gridService.saveBuilding(dto));
    }

    @Operation(summary = "修改楼栋基础信息与楼栋长绑定")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'PROPERTY_ADMIN')")
    @PutMapping("/admin/building")
    @LogOperation(title = "修改楼栋台账", businessType = "UPDATE", saveParam = true)
    public Result<BizBuilding> updateBuilding(@Valid @RequestBody BuildingSaveDto dto) {
        if (dto.getId() == null) {
            return Result.error(400, "修改楼栋时必须携带楼栋ID");
        }
        return Result.success("楼栋信息已更新", gridService.saveBuilding(dto));
    }

    @Operation(summary = "删除楼栋 (级联删除其下单元与房屋,存在居民认证绑定时拒绝)")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'PROPERTY_ADMIN')")
    @DeleteMapping("/admin/building/{id}")
    @LogOperation(title = "删除楼栋台账", businessType = "DELETE")
    public Result<Void> deleteBuilding(@PathVariable Long id) {
        gridService.deleteBuilding(id);
        return Result.success("楼栋及其下单元、房屋台账已删除", null);
    }

    @Operation(summary = "新增单元")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'PROPERTY_ADMIN')")
    @PostMapping("/admin/unit")
    @LogOperation(title = "新增单元", businessType = "INSERT", saveParam = true)
    public Result<BizUnit> createUnit(@Valid @RequestBody UnitSaveDto dto) {
        return Result.success("单元已创建", gridService.saveUnit(dto));
    }

    @Operation(summary = "修改单元信息")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'PROPERTY_ADMIN')")
    @PutMapping("/admin/unit")
    @LogOperation(title = "修改单元", businessType = "UPDATE", saveParam = true)
    public Result<BizUnit> updateUnit(@Valid @RequestBody UnitSaveDto dto) {
        if (dto.getId() == null) {
            return Result.error(400, "修改单元时必须携带单元ID");
        }
        return Result.success("单元信息已更新", gridService.saveUnit(dto));
    }

    @Operation(summary = "删除单元 (级联删除其下房屋,存在居民认证绑定时拒绝)")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'PROPERTY_ADMIN')")
    @DeleteMapping("/admin/unit/{id}")
    @LogOperation(title = "删除单元", businessType = "DELETE")
    public Result<Void> deleteUnit(@PathVariable Long id) {
        gridService.deleteUnit(id);
        return Result.success("单元及其下房屋台账已删除", null);
    }

    @Operation(summary = "新增房屋 (归属小区/楼栋由所属单元自动推导)")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'PROPERTY_ADMIN')")
    @PostMapping("/admin/house")
    @LogOperation(title = "新增房屋", businessType = "INSERT", saveParam = true)
    public Result<BizHouse> createHouse(@Valid @RequestBody HouseSaveDto dto) {
        return Result.success("房屋台账已创建", gridService.saveHouse(dto));
    }

    @Operation(summary = "按楼层区间批量生成房屋台账 (如 3-18 层每层 4 户,跳过已存在门牌)")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'PROPERTY_ADMIN')")
    @PostMapping("/admin/house/batch")
    @LogOperation(title = "批量生成房屋台账", businessType = "INSERT", saveParam = true)
    public Result<Integer> batchGenHouses(@Valid @RequestBody HouseBatchGenDto dto) {
        int generated = gridService.batchGenHouses(dto);
        return Result.success("本次共生成 " + generated + " 户房屋台账", generated);
    }

    @Operation(summary = "修改房屋信息与居住状态")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'PROPERTY_ADMIN')")
    @PutMapping("/admin/house")
    @LogOperation(title = "修改房屋", businessType = "UPDATE", saveParam = true)
    public Result<BizHouse> updateHouse(@Valid @RequestBody HouseSaveDto dto) {
        if (dto.getId() == null) {
            return Result.error(400, "修改房屋时必须携带房屋ID");
        }
        return Result.success("房屋信息已更新", gridService.saveHouse(dto));
    }

    @Operation(summary = "删除房屋 (存在居民认证绑定时拒绝)")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'PROPERTY_ADMIN')")
    @DeleteMapping("/admin/house/{id}")
    @LogOperation(title = "删除房屋", businessType = "DELETE")
    public Result<Void> deleteHouse(@PathVariable Long id) {
        gridService.deleteHouse(id);
        return Result.success("房屋台账已删除", null);
    }
}
