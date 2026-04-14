package org.example.aiboard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.example.aiboard.model.MoodEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
public class MoodBoardService {

    private static final Logger log = LoggerFactory.getLogger(MoodBoardService.class);

    private static final String PROMPT_TEMPLATE = """
            你是温柔、真诚的小伙伴。用户会写下当下心情，你要用中文回复一段暖心、鼓励或陪伴的话，长度大约50个汉字（40到60字之间）。
            不要复述或引用用户原话，不要标题、不要列表、不要引号包裹，直接输出一段话。
            用户的心情：%s
            """;

    private static final String FALLBACK_REPLY =
            "此刻的心情都值得被温柔对待。慢慢来，你已经很努力了，给自己一点喘息，明天也会有一小步光亮等着你。";

    private final ChatLanguageModel chatModel;
    private final List<DefaultMoodTemplate> defaultTemplates;
    private final Map<String, List<MoodEntry>> entriesByVisitor = new ConcurrentHashMap<>();

    public MoodBoardService(
            ObjectProvider<ChatLanguageModel> chatModelProvider,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader) {
        this.chatModel = chatModelProvider.getIfAvailable();
        this.defaultTemplates = loadDefaultTemplates(objectMapper, resourceLoader);
    }

    public MoodEntry addEntry(String visitorId, String content) {
        String warmReply = generateWarmReply(content);
        MoodEntry entry = MoodEntry.create(visitorId, content.trim(), warmReply);
        ensureVisitorEntries(visitorId).add(0, entry);
        return entry;
    }

    public List<MoodEntry> listForVisitor(String visitorId) {
        List<MoodEntry> list = ensureVisitorEntries(visitorId);
        synchronized (list) {
            return List.copyOf(list);
        }
    }

    public void completeEntry(String visitorId, String entryId) {
        List<MoodEntry> list = ensureVisitorEntries(visitorId);
        synchronized (list) {
            for (int i = 0; i < list.size(); i++) {
                MoodEntry e = list.get(i);
                if (!e.id().equals(entryId)) {
                    continue;
                }
                if (e.completed()) {
                    return;
                }
                list.set(i, e.withCompleted(true));
                return;
            }
        }
    }

    public RemoveEntryResult removeEntry(String visitorId, String entryId) {
        List<MoodEntry> list = ensureVisitorEntries(visitorId);
        synchronized (list) {
            for (int i = 0; i < list.size(); i++) {
                MoodEntry e = list.get(i);
                if (!e.id().equals(entryId)) {
                    continue;
                }
                if (e.completed()) {
                    return RemoveEntryResult.COMPLETED_LOCKED;
                }
                list.remove(i);
                return RemoveEntryResult.REMOVED;
            }
        }
        return RemoveEntryResult.NOT_FOUND;
    }

    private List<MoodEntry> ensureVisitorEntries(String visitorId) {
        return entriesByVisitor.computeIfAbsent(
                visitorId,
                v -> Collections.synchronizedList(seedDefaultEntries(v)));
    }

    private List<MoodEntry> seedDefaultEntries(String visitorId) {
        if (defaultTemplates.isEmpty()) {
            return new ArrayList<>();
        }
        List<MoodEntry> seeded = new ArrayList<>(defaultTemplates.size());
        for (DefaultMoodTemplate t : defaultTemplates) {
            MoodEntry entry = MoodEntry.create(visitorId, t.content(), t.warmReply());
            if (t.completed()) {
                entry = entry.withCompleted(true);
            }
            seeded.add(entry);
        }
        return seeded;
    }

    private List<DefaultMoodTemplate> loadDefaultTemplates(
            ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource("classpath:default.json");
        if (!resource.exists()) {
            log.info("default.json not found, skip default seed data");
            return List.of();
        }
        try (InputStream inputStream = resource.getInputStream()) {
            List<DefaultMoodTemplate> raw = objectMapper.readValue(
                    inputStream,
                    new TypeReference<>() {});
            List<DefaultMoodTemplate> cleaned = new ArrayList<>();
            for (DefaultMoodTemplate t : raw) {
                if (t == null || t.content() == null || t.warmReply() == null) {
                    continue;
                }
                String content = t.content().trim();
                String warmReply = t.warmReply().trim();
                if (content.isEmpty() || warmReply.isEmpty()) {
                    continue;
                }
                cleaned.add(new DefaultMoodTemplate(content, warmReply, t.completed()));
            }
            log.info("loaded {} default mood entries from default.json", cleaned.size());
            return List.copyOf(cleaned);
        } catch (Exception e) {
            log.warn("failed to load default.json, skip defaults: {}", e.getMessage());
            return List.of();
        }
    }

    private String generateWarmReply(String userMood) {
        if (chatModel == null) {
            log.warn("No ChatModel bean ");
            return FALLBACK_REPLY;
        }
        try {
            String prompt = String.format(PROMPT_TEMPLATE.trim(), userMood.trim());
            String reply = chatModel.chat(prompt);
            return normalizeReply(reply);
        } catch (Exception e) {
            log.warn("LLM call failed, using fallback reply: {}", e.getMessage());
            return FALLBACK_REPLY;
        }
    }

    private static String normalizeReply(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.strip().replace("\n", " ").replaceAll("\\s+", " ");
        if (s.length() > 120) {
            s = s.substring(0, 120) + "…";
        }
        return s;
    }

    private record DefaultMoodTemplate(String content, String warmReply, boolean completed) {}
}
