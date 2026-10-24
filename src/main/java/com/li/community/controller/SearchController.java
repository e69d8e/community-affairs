package com.li.community.controller;

import com.li.community.common.result.PageResult;
import com.li.community.common.result.Result;
import com.li.community.model.document.SearchItemDoc;
import com.li.community.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "08. Elasticsearch 8.x 全文搜索引擎模块")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "全网多维度关键词全文检索 (IK分词与高亮; 非平台管理员仅检索本人小区数据)")
    @GetMapping
    public Result<PageResult<SearchItemDoc>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) Long communityId,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(searchService.search(keyword, itemType, communityId, page, size));
    }

    @Operation(summary = "管理员手动触发全量数据重新同步至 Elasticsearch")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PostMapping("/admin/sync-all")
    public Result<Void> syncAll() {
        searchService.syncAllToEs();
        return Result.success("ES 全文索引库全量重构同步完成", null);
    }

    @Operation(summary = "获取社区实时热搜词排行 (基于 Redis ZSet)")
    @GetMapping("/hot")
    public Result<List<String>> getHotSearchWords() {
        return Result.success(searchService.getHotSearchWords());
    }
}
