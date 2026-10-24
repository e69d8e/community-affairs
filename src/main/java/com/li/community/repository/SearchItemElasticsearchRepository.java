package com.li.community.repository;

import com.li.community.model.document.SearchItemDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SearchItemElasticsearchRepository extends ElasticsearchRepository<SearchItemDoc, String> {

    Page<SearchItemDoc> findByTitleContainingOrContentContaining(String title, String content, Pageable pageable);

    Page<SearchItemDoc> findByItemTypeAndTitleContaining(String itemType, String title, Pageable pageable);

    List<SearchItemDoc> findByTargetIdAndItemType(Long targetId, String itemType);
}
