package com.li.community.model.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "community_search_index")
public class SearchItemDoc implements Serializable {

    @Id
    private String id; // 格式: NOTICE_{id}, ACTIVITY_{id}, MARKET_{id}

    @Field(type = FieldType.Keyword)
    private String itemType; // NOTICE, ACTIVITY, MARKET

    @Field(type = FieldType.Long)
    private Long targetId;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String title;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Long)
    private Long communityId;

    @Field(type = FieldType.Keyword)
    private String coverImage;

    @Field(type = FieldType.Date)
    private Date publishTime;

    @Field(type = FieldType.Keyword)
    private String extraInfo; // 价格/地点/发起人等展示摘要
}
