package com.li.community.service;

import com.li.community.common.result.PageResult;
import com.li.community.model.dto.ReportSubmitDto;
import com.li.community.model.dto.SensitiveWordDto;
import com.li.community.model.entity.BizReport;
import com.li.community.model.entity.BizSensitiveWord;

import java.util.Set;

public interface ModerationService {

    boolean containsSensitiveWord(String text);

    /** 文本命中需直接拒绝 (action=REJECT) 的敏感词 */
    boolean containsRejectWord(String text);

    /** 返回需直接拒绝的命中敏感词集合 */
    Set<String> findRejectWords(String text);

    String replaceSensitiveWords(String text);

    Set<String> findSensitiveWords(String text);

    PageResult<BizSensitiveWord> listWords(String keyword, Long page, Long size);

    void saveWord(SensitiveWordDto dto);

    void deleteWord(Long id);

    void reloadDfaTree();

    void submitReport(ReportSubmitDto dto);

    PageResult<BizReport> listReports(Integer status, Long communityId, Long page, Long size);

    void handleReport(Long reportId, Integer status, String remark);
}
