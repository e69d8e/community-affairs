package com.li.community.ai.service.fallback;

import com.li.community.model.dto.AiOrderTriageDto;
import com.li.community.model.vo.AiOrderParseVo;
import com.li.community.model.vo.AiOrderTriageVo;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AiMockEngine {

    /**
     * 高频社区办事指南知识库 (RAG 本地兜底词条)
     */
    private static final List<FaqEntry> FAQ_ENTRIES = new ArrayList<>();

    static {
        // 1. 居住证明与房产认证
        FAQ_ENTRIES.add(new FaqEntry(
                List.of("居住证明", "房产认证", "房屋认证", "租房认证", "业主认证", "入驻认证", "开证明"),
                "办理社区居住证明或房产认证指引：\n\n" +
                "1. **线上办理**：请登录本平台，点击右上角进入「个人中心」-「我的房产」，选择您所在的小区、楼栋与户室提交认证申请，并上传购房合同、房产证或合法租房合同照片。\n" +
                "2. **审核时效**：您所在楼栋的责任网格员将在 1~3 个工作日内完成线上初审与系统核验；通过后即可享受完整的业主/居民在线办事权限。\n" +
                "3. **纸质盖章**：如升学或落户需纸质凭证，线上审核通过后可凭身份证前往社区党群服务中心综合窗口现场打印盖章。"
        ));

        // 2. 电瓶车充电与停放
        FAQ_ENTRIES.add(new FaqEntry(
                List.of("电瓶车", "电动车", "充电桩", "充不上电", "充电收费", "飞线充电", "推车上楼"),
                "社区电瓶车规范停放与安全充电说明：\n\n" +
                "1. **集中充电区域**：小区各住宅楼架空层及地面集中车棚均已铺设智能扫码充电桩，严禁将电动车或蓄电池推入电梯、推车进户及私拉飞线充电。\n" +
                "2. **收费标准**：按照惠民电价计费，基础标准为 1 元/4 小时，充满自动断电并向车主微信推送断电提醒。\n" +
                "3. **故障报修**：若发现充电桩绿灯常亮但无法取电，可在平台「在线报修」提交工单，物业工程师将快速检修。"
        ));

        // 3. 报修与故障报修标准
        FAQ_ENTRIES.add(new FaqEntry(
                List.of("报修", "漏水", "跳闸", "断水", "停水", "断电", "水管", "水龙头", "维修收费", "维修电话"),
                "社区公共与户内设施报修指南：\n\n" +
                "1. **申报通道**：点击平台「在线报修/投诉」或使用本助手一键唤起申报表单，输入您的报修位置与故障现场照片。\n" +
                "2. **责任划分**：\n" +
                "   - **公共部位**（楼栋电梯、公共照明、外墙、消防管网）：由物业日常维保或动用公共维修基金维修，居民无需承担费用；\n" +
                "   - **自用部位**（户内开关、水龙头、室内暗管）：按物价部门备案的物业维修工本费标准公示收费。\n" +
                "3. **响应承诺**：紧急工单（如管道爆裂漏水、大面积停电、电梯困人）15 分钟内网格员与师傅到场；普通工单 2 小时内预约上门。"
        ));

        // 4. 文体活动与报名核销
        FAQ_ENTRIES.add(new FaqEntry(
                List.of("活动", "文体活动", "公益", "讲座", "核销", "二维码", "比赛", "报名"),
                "社区活动参与与核销指南：\n\n" +
                "1. **活动浏览与报名**：点击导航栏「文体活动」，查看社区近期的公益义诊、青少年书画、乒乓球赛等丰富活动，选定后点击「立即报名」即可锁定名额。\n" +
                "2. **名额防超卖**：系统采用原子高并发锁定机制，名额扣完即止，先到先得。\n" +
                "3. **现场签到**：报名成功后可在活动详情页查看您的专属 6 位防伪核销码与动态二维码，活动当天向现场网格员出示即可扫码签到！"
        ));

        // 5. 垃圾分类与投放
        FAQ_ENTRIES.add(new FaqEntry(
                List.of("垃圾分类", "投放时间", "厨余垃圾", "可回收", "大件垃圾", "装修垃圾"),
                "社区生活垃圾定时定点投放指引：\n\n" +
                "1. **投放时间段**：每日早间 07:00 - 09:00，晚间 18:00 - 20:30。非投放时段箱门自动上锁。\n" +
                "2. **误时投放点**：错过投放时段的居民，请前往小区东侧物业用房旁的「24小时误时投放驿站」。\n" +
                "3. **大件与装修垃圾**：旧沙发、床垫或装修碎砖严禁堆放在楼道或绿化带，请联系物业客服预约集中堆放点清运。"
        ));

        // 6. 邻里集市与互助
        FAQ_ENTRIES.add(new FaqEntry(
                List.of("二手", "闲置", "互助", "集市", "借物", "拼车", "买卖"),
                "邻里闲置与互助交流平台规则：\n\n" +
                "1. **发布流程**：进入「闲置与互助」版块，点击发布帖子，上传闲置物品或求助需求照片，标明转让价格与交付方式（自提/送上门）。\n" +
                "2. **即时私信**：买家对物品感兴趣可直接发起点对点在线私信，实时协商交易细节。\n" +
                "3. **诚信友善**：发帖内容受智能语义安全审核保护，请勿发布违禁品或引战不良言论，共同维护友爱睦邻空间。"
        ));
    }

    /**
     * 匹配高频 FAQ 问答
     */
    public String matchFaq(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        String lower = prompt.toLowerCase();
        for (FaqEntry entry : FAQ_ENTRIES) {
            for (String kw : entry.keywords) {
                if (lower.contains(kw.toLowerCase())) {
                    return entry.answer;
                }
            }
        }
        return null;
    }

    /**
     * 意图识别并推荐直达卡片 Action Card
     */
    public Map<String, Object> detectActionCard(String prompt, String answer) {
        String combined = (prompt + " " + (answer != null ? answer : "")).toLowerCase();

        if (combined.contains("报修") || combined.contains("漏水") || combined.contains("坏了") 
                || combined.contains("水管") || combined.contains("跳闸") || combined.contains("停水")
                || combined.contains("工单") || combined.contains("投诉")) {
            Map<String, Object> card = new HashMap<>();
            card.put("type", "route");
            card.put("target", "/orders");
            card.put("title", "在线工单申报");
            card.put("desc", "需要水电报修或反映物业问题？点击直接进入申报中心");
            card.put("btnText", "立即在线报修/投诉");
            card.put("icon", "Tools");
            return card;
        }

        if (combined.contains("活动") || combined.contains("讲座") || combined.contains("报名") 
                || combined.contains("核销") || combined.contains("比赛") || combined.contains("义诊")) {
            Map<String, Object> card = new HashMap<>();
            card.put("type", "route");
            card.put("target", "/activities");
            card.put("title", "社区文体公益活动");
            card.put("desc", "邻里乒乓球赛、义诊、青少年书画讲座火热报名中");
            card.put("btnText", "浏览最新活动");
            card.put("icon", "Calendar");
            return card;
        }

        if (combined.contains("房产") || combined.contains("居住证") || combined.contains("认证") 
                || combined.contains("开证明") || combined.contains("房号") || combined.contains("业主")) {
            Map<String, Object> card = new HashMap<>();
            card.put("type", "route");
            card.put("target", "/user/profile");
            card.put("title", "房产认证与个人中心");
            card.put("desc", "完成房屋权属绑定或租户登记，尊享完整数字化治理服务");
            card.put("btnText", "前往办理认证");
            card.put("icon", "House");
            return card;
        }

        if (combined.contains("二手") || combined.contains("闲置") || combined.contains("互助") 
                || combined.contains("集市") || combined.contains("借物") || combined.contains("转让")) {
            Map<String, Object> card = new HashMap<>();
            card.put("type", "route");
            card.put("target", "/market");
            card.put("title", "邻里闲置与互助集市");
            card.put("desc", "绿色循环好物，邻里零距离转让与互助求助");
            card.put("btnText", "逛逛闲置集市");
            card.put("icon", "ShoppingBag");
            return card;
        }

        return null;
    }

    /**
     * 口语化报修解析 Mock 引擎
     */
    public AiOrderParseVo parseOrderMock(String rawText) {
        String text = rawText != null ? rawText.trim() : "";
        String lower = text.toLowerCase();

        String category = "公用设施";
        String title = "社区设施报修申报";
        int isUrgent = 0;
        String urgentReason = "常见普通日常报修，师傅将在预约时段内上门检查。";

        if (lower.contains("水") || lower.contains("漏水") || lower.contains("水管") 
                || lower.contains("龙头") || lower.contains("管道") || lower.contains("渗水")
                || lower.contains("下水道") || lower.contains("堵了")) {
            category = "给排水系统";
            title = "室内/公用给排水管道故障报修";
            if (lower.contains("爆") || lower.contains("喷") || lower.contains("淹") || lower.contains("急") || lower.contains("一直漏")) {
                isUrgent = 1;
                urgentReason = "检测到管道破裂持续涌水，存在浸湿楼板及引发电气次生灾害隐患，标记为紧急！";
            }
        } else if (lower.contains("电") || lower.contains("跳闸") || lower.contains("断电") 
                || lower.contains("插座") || lower.contains("线路") || lower.contains("冒烟")
                || lower.contains("电线") || lower.contains("火花")) {
            category = "强电弱电";
            title = "室内供电/电气线路故障报修";
            if (lower.contains("冒烟") || lower.contains("火花") || lower.contains("全部断电") || lower.contains("危险")) {
                isUrgent = 1;
                urgentReason = "现场存在线路异响、冒烟或全部停电隐患，存在火灾次生风险，升级为紧急工单！";
            }
        } else if (lower.contains("电梯") || lower.contains("轿厢") || lower.contains("停梯") || lower.contains("困人")) {
            category = "电梯公用";
            title = "单元电梯运行故障报修";
            isUrgent = 1;
            urgentReason = "电梯属于特种高危特种设备，涉及居民人身通行安全，一律按紧急优先响应！";
        } else if (lower.contains("门禁") || lower.contains("大门") || lower.contains("监控") || lower.contains("道闸")) {
            category = "门禁安防";
            title = "门禁系统与安防道闸故障报修";
        } else if (lower.contains("垃圾") || lower.contains("臭") || lower.contains("脏") || lower.contains("绿化")) {
            category = "环境卫生";
            title = "小区楼道与公共环境卫生保洁工单";
        }

        String standardContent = String.format("居民在线申报反馈：%s。经 AI 辅助分析，归属【%s】领域，建议责任人员携带专业检测工具预约核验。", 
                text, category);

        return AiOrderParseVo.builder()
                .title(title)
                .category(category)
                .isUrgent(isUrgent)
                .urgentReason(urgentReason)
                .standardContent(standardContent)
                .build();
    }

    /**
     * 管理端工单研判 Mock 引擎
     */
    public AiOrderTriageVo triageOrderMock(AiOrderTriageDto dto) {
        String category = dto.getCategory() != null ? dto.getCategory() : "设施";
        String content = dto.getContent() != null ? dto.getContent() : "";

        String faultDiagnosis;
        String maintenanceTips;
        String recommendedRole;
        double estimatedHours = 1.5;
        String suggestedAuditRemark;

        if (category.contains("给排水") || content.contains("水")) {
            faultDiagnosis = "初步研判：高层水管水锤压力冲击或接口老化导致管件接头松动渗水，部分情况伴随地漏回返或密封胶脱落。";
            maintenanceTips = "1. 到场前致电业主指导关闭户内冷热水进水阀门；2. 携带 PPR 热熔焊机、生料带、管钳及备用 20/25mm 弯头管件；3. 主动勘验楼下天花板是否存在渗斑。";
            recommendedRole = "综合维修工 (水电水暖专长)";
            estimatedHours = 1.5;
            suggestedAuditRemark = "已初审通过。属给排水故障，已推荐派发水暖技能维修师傅，请务必备好管钳与防护用具尽速排查。";
        } else if (category.contains("电") || content.contains("电")) {
            faultDiagnosis = "初步研判：支路空气开关瞬时过载跳闸，或电器绝缘皮老化破损引起微弱漏电保护动作。";
            maintenanceTips = "1. 上门前务必佩戴绝缘手套与防电护具；2. 使用万用表和兆欧表分段逐路测量线路对地绝缘电阻；3. 排查大功率用电设备插座接触点是否碳化。";
            recommendedRole = "低压电工 (特种作业持证)";
            estimatedHours = 1.0;
            suggestedAuditRemark = "已审核通过。涉及用电安全，请安排持特种作业操作证的专业电工师傅上门，严禁违章带电检修。";
        } else if (category.contains("电梯") || content.contains("梯")) {
            faultDiagnosis = "初步研判：电梯安全回路传感器灵敏度漂移、光幕脏污感应异常或轿厢平层感应器间隙过大。";
            maintenanceTips = "1. 首要确认轿厢内无被困乘客；2. 在首层及故障楼层设置停梯安全护栏与警示标牌；3. 调取主板故障代码并联动维保原厂驻点工程师联合检修。";
            recommendedRole = "电梯特种设备维保专员";
            estimatedHours = 2.0;
            suggestedAuditRemark = "已加急审核。电梯公共设施涉及特种安全，已通知专业驻点维保机构即刻到场，做好围挡与居民告示。";
        } else {
            faultDiagnosis = "初步研判：公共基础设施日常磨损或损耗性部件自然老化，需实地勘查定损。";
            maintenanceTips = "1. 联系申报人约定精确上门时间；2. 拍照留存处置前现场原始状态凭证；3. 维修完毕后清理施工废料垃圾并请业主签署确认单。";
            recommendedRole = "物业通用综合维修员";
            estimatedHours = 2.0;
            suggestedAuditRemark = "工单审核通过。请按标准物业服务规范及时派单，完工后上传施工照片与修复验收凭据。";
        }

        return AiOrderTriageVo.builder()
                .faultDiagnosis(faultDiagnosis)
                .maintenanceTips(maintenanceTips)
                .recommendedRole(recommendedRole)
                .estimatedHours(estimatedHours)
                .suggestedAuditRemark(suggestedAuditRemark)
                .build();
    }

    private static class FaqEntry {
        List<String> keywords;
        String answer;

        FaqEntry(List<String> keywords, String answer) {
            this.keywords = keywords;
            this.answer = answer;
        }
    }
}
