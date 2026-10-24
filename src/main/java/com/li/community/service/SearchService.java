package com.li.community.service;

import com.li.community.common.result.PageResult;
import com.li.community.model.document.SearchItemDoc;

import java.util.List;

public interface SearchService {

    PageResult<SearchItemDoc> search(String keyword, String itemType, Long communityId, Long page, Long size);

    void syncAllToEs();

    List<String> getHotSearchWords();
}
