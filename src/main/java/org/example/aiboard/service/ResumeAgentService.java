package org.example.aiboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.example.aiboard.dto.CriticReportDto;
import org.example.aiboard.dto.InitialRewriteResponse;
import org.example.aiboard.dto.ResumeChangeDto;
import org.example.aiboard.dto.ReviewRewriteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ResumeAgentService {

    private static final Logger log = LoggerFactory.getLogger(ResumeAgentService.class);
    private static final int MAX_MODEL_TEXT = 12_000;

    private final ChatLanguageModel chatModel;
    private final ObjectMapper objectMapper;

    public ResumeAgentService(ObjectProvider<ChatLanguageModel> chatModelProvider, ObjectMapper objectMapper) {
        this.chatModel = chatModelProvider.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    public InitialRewriteResponse initialRewrite(String resumeText, String jd) {
        String prompt = """
                你是 Writer Agent，任务是根据岗位 JD 改写候选人的中文简历。
                要求：
                1. 只基于原简历中已有事实改写，不编造公司、项目、指标、年限、学历。
                2. 优先强化与 JD 相关的技能、项目职责、业务结果和关键词。
                3. 输出 JSON，不要 Markdown，不要解释。
                JSON 格式：
                {
                  "rewrittenResume": "完整的新简历文本",
                  "changes": [
                    {
                      "section": "改动所在模块",
                      "beforeText": "原文片段，没有则写空字符串",
                      "afterText": "改写后片段",
                      "reason": "为什么这样改"
                    }
                  ]
                }

                原简历：
                %s

                岗位 JD：
                %s
                """.formatted(limit(resumeText), limit(jd));
        try {
            JsonNode root = callJson(prompt);
            String rewritten = text(root, "rewrittenResume", resumeText);
            List<ResumeChangeDto> changes = parseChanges(root.path("changes"));
            if (changes.isEmpty()) {
                changes = fallbackChanges(resumeText, jd, false);
            }
            return new InitialRewriteResponse(resumeText, rewritten, changes);
        } catch (Exception e) {
            log.warn("initial writer agent failed, using fallback: {}", e.getMessage());
            List<ResumeChangeDto> changes = fallbackChanges(resumeText, jd, false);
            return new InitialRewriteResponse(resumeText, applyChanges(resumeText, changes), changes);
        }
    }

    public ReviewRewriteResponse reviewAndRewrite(String currentResume, String jd, List<ResumeChangeDto> confirmedChanges) {
        CriticReportDto report = critic(currentResume, jd);
        String prompt = """
                你是 Writer Agent。Critic Agent 已经评估了简历，请根据评估结果做第二轮改写。
                规则：
                1. 仍然不能编造事实；如果缺少事实，只能把表达变得更清晰，或提示需要补充。
                2. 这轮输出的 changes 是“建议保留/不保留”的改动点，用户不能编辑文本。
                3. 输出 JSON，不要 Markdown。
                JSON 格式：
                {
                  "rewrittenResume": "二次优化后的完整简历文本",
                  "changes": [
                    {
                      "section": "模块",
                      "beforeText": "当前简历片段",
                      "afterText": "二次优化片段",
                      "reason": "对应 Critic 哪个问题"
                    }
                  ]
                }

                当前简历：
                %s

                已确认的一轮改动：
                %s

                岗位 JD：
                %s

                Critic 评估：
                %s
                """.formatted(
                limit(currentResume),
                limit(toJson(confirmedChanges)),
                limit(jd),
                limit(toJson(report)));
        try {
            JsonNode root = callJson(prompt);
            String rewritten = text(root, "rewrittenResume", currentResume);
            List<ResumeChangeDto> changes = parseChanges(root.path("changes"));
            if (changes.isEmpty()) {
                changes = fallbackChanges(currentResume, jd, true);
            }
            return new ReviewRewriteResponse(report, rewritten, changes);
        } catch (Exception e) {
            log.warn("second writer agent failed, using fallback: {}", e.getMessage());
            List<ResumeChangeDto> changes = fallbackChanges(currentResume, jd, true);
            return new ReviewRewriteResponse(report, applyChanges(currentResume, changes), changes);
        }
    }

    private CriticReportDto critic(String resumeText, String jd) {
        String prompt = """
                你是 Critic Agent。请评估简历：
                1. 是否符合 JD 的要求。
                2. 简历是否完整，是否缺少基础信息、经历、项目、技能、教育、量化成果等。
                输出 JSON，不要 Markdown。
                JSON 格式：
                {
                  "jdMatchScore": 0到100整数,
                  "completenessScore": 0到100整数,
                  "strengths": ["优势"],
                  "risks": ["风险"],
                  "missingItems": ["缺失项"]
                }

                简历：
                %s

                岗位 JD：
                %s
                """.formatted(limit(resumeText), limit(jd));
        try {
            JsonNode root = callJson(prompt);
            return new CriticReportDto(
                    clamp(root.path("jdMatchScore").asInt(76)),
                    clamp(root.path("completenessScore").asInt(72)),
                    parseStringList(root.path("strengths")),
                    parseStringList(root.path("risks")),
                    parseStringList(root.path("missingItems")));
        } catch (Exception e) {
            log.warn("critic agent failed, using fallback: {}", e.getMessage());
            return new CriticReportDto(
                    76,
                    72,
                    List.of("简历已有可复用经历，适合围绕 JD 关键词重新组织表达"),
                    List.of("岗位关键词覆盖还不够集中，部分项目结果缺少量化呈现"),
                    List.of("建议补充核心技能栈、项目指标、最近一段经历的业务成果"));
        }
    }

    public String applyChanges(String resumeText, List<ResumeChangeDto> changes) {
        String updated = resumeText == null ? "" : resumeText;
        if (changes == null) {
            return updated;
        }
        for (ResumeChangeDto change : changes) {
            if (change == null || blank(change.afterText())) {
                continue;
            }
            if (!blank(change.beforeText()) && updated.contains(change.beforeText())) {
                updated = updated.replace(change.beforeText(), change.afterText());
            } else if (!updated.contains(change.afterText())) {
                updated = updated.strip() + "\n\n" + change.afterText().strip();
            }
        }
        return updated.strip();
    }

    private JsonNode callJson(String prompt) throws Exception {
        if (chatModel == null) {
            throw new IllegalStateException("ChatLanguageModel is not configured");
        }
        String raw = chatModel.chat(prompt);
        return objectMapper.readTree(extractJson(raw));
    }

    private List<ResumeChangeDto> parseChanges(JsonNode node) {
        List<ResumeChangeDto> changes = new ArrayList<>();
        if (!node.isArray()) {
            return changes;
        }
        for (JsonNode item : node) {
            String afterText = text(item, "afterText", "");
            if (blank(afterText)) {
                continue;
            }
            changes.add(new ResumeChangeDto(
                    UUID.randomUUID().toString(),
                    text(item, "section", "简历内容"),
                    text(item, "beforeText", ""),
                    afterText,
                    text(item, "reason", "提升与 JD 的匹配度")));
        }
        return changes;
    }

    private List<String> parseStringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (!blank(item.asText())) {
                    values.add(item.asText());
                }
            }
        }
        return values;
    }

    private List<ResumeChangeDto> fallbackChanges(String resumeText, String jd, boolean secondRound) {
        String section = secondRound ? "二次优化建议" : "JD 匹配摘要";
        String keywords = jd == null ? "" : jd.replaceAll("\\s+", " ").strip();
        if (keywords.length() > 90) {
            keywords = keywords.substring(0, 90);
        }
        String after = secondRound
                ? "建议在核心优势或项目经历中补充与 JD 直接相关的成果表达，并明确技术栈、职责边界和可量化结果。"
                : "围绕目标岗位强化表达：突出与「" + keywords + "」相关的项目、技能、协作对象和业务结果。";
        return List.of(new ResumeChangeDto(
                UUID.randomUUID().toString(),
                section,
                "",
                after,
                secondRound ? "Critic 认为匹配度和完整度还可提升" : "先建立与 JD 的显性关联"));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private static String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        String text = raw.strip();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || blank(value.asText())) {
            return fallback;
        }
        return value.asText().strip();
    }

    private static String limit(String text) {
        if (text == null) {
            return "";
        }
        String stripped = text.strip();
        if (stripped.length() <= MAX_MODEL_TEXT) {
            return stripped;
        }
        return stripped.substring(0, MAX_MODEL_TEXT);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
