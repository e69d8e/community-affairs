package com.li.community.controller;

import com.li.community.common.result.PageResult;
import com.li.community.common.result.Result;
import com.li.community.model.entity.*;
import com.li.community.model.vo.UserVo;
import com.li.community.service.GridService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @Operation(summary = "管理端获取待审核房屋绑定申请 (网格员仅可见本人负责楼栋)")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'COMMUNITY_ADMIN', 'PROPERTY_ADMIN', 'GRID_OFFICER')")
    @GetMapping("/admin/house-bind/list")
    public Result<PageResult<BizResidentHouse>> listPendingHouseBindings(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(gridService.listPendingHouseBindings(page, size));
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
}
