package com.li.community.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.community.common.exception.BizException;
import com.li.community.common.exception.ErrorCode;
import com.li.community.common.result.PageResult;
import com.li.community.mapper.BizCommunityMapper;
import com.li.community.model.dto.CommunitySaveDto;
import com.li.community.model.entity.BizCommunity;
import com.li.community.service.CommunityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityServiceImpl implements CommunityService {

    private final BizCommunityMapper communityMapper;

    @Override
    public PageResult<BizCommunity> listCommunities(String keyword, Long page, Long size) {
        Page<BizCommunity> pageParam = new Page<>(page != null ? page : 1, size != null ? size : 10);
        Page<BizCommunity> result = communityMapper.selectPage(
                pageParam,
                new LambdaQueryWrapper<BizCommunity>()
                        .and(keyword != null && !keyword.trim().isEmpty(), w ->
                                w.like(BizCommunity::getName, keyword.trim())
                                        .or()
                                        .like(BizCommunity::getCode, keyword.trim())
                                        .or()
                                        .like(BizCommunity::getAddress, keyword.trim()))
                        .orderByAsc(BizCommunity::getId)
        );
        return PageResult.of(result.getRecords(), result.getTotal(), result.getSize(), result.getCurrent());
    }

    @Override
    public void createCommunity(CommunitySaveDto dto) {
        checkCodeUnique(dto.getCode(), null);

        BizCommunity community = BizCommunity.builder()
                .id(IdUtil.getSnowflakeNextId())
                .name(dto.getName())
                .code(dto.getCode())
                .address(dto.getAddress())
                .province(dto.getProvince())
                .city(dto.getCity())
                .district(dto.getDistrict())
                .contactPerson(dto.getContactPerson())
                .contactPhone(dto.getContactPhone())
                .longitude(dto.getLongitude())
                .latitude(dto.getLatitude())
                .description(dto.getDescription())
                .status(dto.getStatus() != null ? dto.getStatus() : 1)
                .build();
        communityMapper.insert(community);
        log.info("新增小区: id={}, name={}", community.getId(), community.getName());
    }

    @Override
    public void updateCommunity(CommunitySaveDto dto) {
        if (dto.getId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "更新小区时必须携带小区ID");
        }
        BizCommunity existing = communityMapper.selectById(dto.getId());
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "小区不存在");
        }
        checkCodeUnique(dto.getCode(), dto.getId());

        existing.setName(dto.getName());
        existing.setCode(dto.getCode());
        existing.setAddress(dto.getAddress());
        existing.setProvince(dto.getProvince());
        existing.setCity(dto.getCity());
        existing.setDistrict(dto.getDistrict());
        existing.setContactPerson(dto.getContactPerson());
        existing.setContactPhone(dto.getContactPhone());
        existing.setLongitude(dto.getLongitude());
        existing.setLatitude(dto.getLatitude());
        existing.setDescription(dto.getDescription());
        communityMapper.updateById(existing);
    }

    @Override
    public void updateCommunityStatus(Long communityId, Integer status) {
        BizCommunity community = communityMapper.selectById(communityId);
        if (community == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "小区不存在");
        }
        community.setStatus(status);
        communityMapper.updateById(community);
    }

    private void checkCodeUnique(String code, Long excludeId) {
        Long count = communityMapper.selectCount(
                new LambdaQueryWrapper<BizCommunity>()
                        .eq(BizCommunity::getCode, code)
                        .ne(excludeId != null, BizCommunity::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "小区编号已被占用: " + code);
        }
    }
}
