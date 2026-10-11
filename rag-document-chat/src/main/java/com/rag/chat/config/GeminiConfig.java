package com.rag.chat.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class GeminiConfig {

    private static final Logger log = LoggerFactory.getLogger(GeminiConfig.class);

    @Value("${gemini.api-key:}")
    private String defaultApiKey;

    @Value("${gemini.chat-model:gemini-2.0-flash}")
    private String chatModelName;

    @Value("${gemini.embedding-model:gemini-embedding-001}")
    private String embeddingModelName;

    private final Map<String, ChatModel> chatModelCache = new ConcurrentHashMap<>();
    private final Map<String, EmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();

    @Bean
    public InMemoryEmbeddingStore<TextSegment> inMemoryEmbeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    public String resolveApiKey(String requestKey) {
        if (requestKey != null && !requestKey.isBlank()) {
            return requestKey.trim();
        }
        if (defaultApiKey != null && !defaultApiKey.isBlank()) {
            return defaultApiKey.trim();
        }
        throw new IllegalArgumentException(
                "Chưa cấu hình Gemini API Key! Vui lòng nhập API Key trên giao diện hoặc đặt biến môi trường GEMINI_API_KEY."
        );
    }

    public ChatModel getChatModel(String requestKey) {
        String key = resolveApiKey(requestKey);
        return chatModelCache.computeIfAbsent(key, k -> new ResilientGeminiChatModel(k, chatModelName));
    }

    public EmbeddingModel getEmbeddingModel(String requestKey) {
        String key = resolveApiKey(requestKey);
        return embeddingModelCache.computeIfAbsent(key, k -> new ResilientGeminiEmbeddingModel(k, embeddingModelName));
    }

    /**
     * ChatModel bọc ngoài tự động thử các tên model đang hoạt động trên Google AI Studio:
     * gemini-2.0-flash -> gemini-2.5-flash -> gemini-1.5-flash-latest -> gemini-1.5-flash -> gemini-1.5-pro
     */
    public static class ResilientGeminiChatModel implements ChatModel {
        private final String apiKey;
        private final List<String> candidateModels;
        private volatile String workingModel;

        public ResilientGeminiChatModel(String apiKey, String preferredModel) {
            this.apiKey = apiKey;
            this.workingModel = preferredModel;
            this.candidateModels = Arrays.asList(
                    preferredModel,
                    "gemini-2.0-flash",
                    "gemini-2.5-flash",
                    "gemini-1.5-flash-latest",
                    "gemini-1.5-flash-8b",
                    "gemini-1.5-flash",
                    "gemini-1.5-pro"
            );
        }

        private GoogleAiGeminiChatModel createModel(String modelName) {
            return GoogleAiGeminiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .temperature(0.2)
                    .build();
        }

        @Override
        public String chat(String prompt) {
            Exception lastException = null;

            if (workingModel != null) {
                try {
                    return createModel(workingModel).chat(prompt);
                } catch (Exception e) {
                    log.warn("Model chat {} thất bại: {}. Đang thử model thay thế...", workingModel, e.getMessage());
                    lastException = e;
                }
            }

            for (String candidate : candidateModels) {
                if (candidate.equals(workingModel)) continue;
                try {
                    log.info("Đang thử model chat: {}", candidate);
                    String response = createModel(candidate).chat(prompt);
                    this.workingModel = candidate;
                    log.info("Model chat hoạt động thành công: {}", candidate);
                    return response;
                } catch (Exception e) {
                    log.warn("Thử model chat {} không thành công: {}", candidate, e.getMessage());
                    lastException = e;
                }
            }

            throw new RuntimeException("Không tìm thấy model Chat khả dụng trên Google Gemini API: " +
                    (lastException != null ? lastException.getMessage() : "Lỗi không xác định"), lastException);
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            Exception lastException = null;

            if (workingModel != null) {
                try {
                    return createModel(workingModel).chat(request);
                } catch (Exception e) {
                    log.warn("Model chat {} thất bại: {}. Đang thử model thay thế...", workingModel, e.getMessage());
                    lastException = e;
                }
            }

            for (String candidate : candidateModels) {
                if (candidate.equals(workingModel)) continue;
                try {
                    log.info("Đang thử model chat: {}", candidate);
                    ChatResponse response = createModel(candidate).chat(request);
                    this.workingModel = candidate;
                    log.info("Model chat hoạt động thành công: {}", candidate);
                    return response;
                } catch (Exception e) {
                    log.warn("Thử model chat {} không thành công: {}", candidate, e.getMessage());
                    lastException = e;
                }
            }

            throw new RuntimeException("Không tìm thấy model Chat khả dụng trên Google Gemini API: " +
                    (lastException != null ? lastException.getMessage() : "Lỗi không xác định"), lastException);
        }

        @Override
        public ChatResponse chat(List<ChatMessage> messages) {
            return chat(ChatRequest.builder().messages(messages).build());
        }
    }

    /**
     * EmbeddingModel bọc ngoài tự động thử các tên model đang hoạt động trên Google AI Studio:
     * gemini-embedding-001 -> embedding-001 -> gemini-embedding-2-preview -> text-embedding-004
     */
    public static class ResilientGeminiEmbeddingModel implements EmbeddingModel {
        private final String apiKey;
        private final List<String> candidateModels;
        private volatile String workingModel;

        public ResilientGeminiEmbeddingModel(String apiKey, String preferredModel) {
            this.apiKey = apiKey;
            this.workingModel = preferredModel;
            this.candidateModels = Arrays.asList(
                    preferredModel,
                    "gemini-embedding-001",
                    "embedding-001",
                    "gemini-embedding-2-preview",
                    "text-embedding-004"
            );
        }

        private EmbeddingModel createModel(String modelName) {
            return GoogleAiEmbeddingModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .build();
        }

        @Override
        public Response<Embedding> embed(String text) {
            return embed(TextSegment.from(text));
        }

        @Override
        public Response<Embedding> embed(TextSegment textSegment) {
            Response<List<Embedding>> response = embedAll(List.of(textSegment));
            return Response.from(response.content().get(0), response.tokenUsage(), response.finishReason());
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            Exception lastException = null;

            if (workingModel != null) {
                try {
                    return createModel(workingModel).embedAll(textSegments);
                } catch (Exception e) {
                    log.warn("Model embedding {} thất bại: {}. Đang thử các model thay thế...", workingModel, e.getMessage());
                    lastException = e;
                }
            }

            for (String candidate : candidateModels) {
                if (candidate.equals(workingModel)) continue;
                try {
                    log.info("Đang thử model embedding: {}", candidate);
                    Response<List<Embedding>> response = createModel(candidate).embedAll(textSegments);
                    this.workingModel = candidate;
                    log.info("Model embedding hoạt động thành công: {}", candidate);
                    return response;
                } catch (Exception e) {
                    log.warn("Thử model {} không thành công: {}", candidate, e.getMessage());
                    lastException = e;
                }
            }

            throw new RuntimeException("Không tìm thấy model Embedding khả dụng trên Google Gemini API: " +
                    (lastException != null ? lastException.getMessage() : "Lỗi không xác định"), lastException);
        }
    }
}
