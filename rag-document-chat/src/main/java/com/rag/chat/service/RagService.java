package com.rag.chat.service;

import com.rag.chat.config.GeminiConfig;
import com.rag.chat.dto.ChatResponseDto;
import com.rag.chat.dto.CitationDto;
import com.rag.chat.dto.MemoryStatusDto;
import com.rag.chat.dto.UploadResponseDto;
import com.rag.chat.model.DocumentChunk;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final DocumentParserService documentParserService;
    private final GeminiConfig geminiConfig;
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;

    // Quản lý danh sách file đã lưu trong RAM
    private final Set<String> uploadedDocuments = new CopyOnWriteArraySet<>();

    public RagService(DocumentParserService documentParserService,
                      GeminiConfig geminiConfig,
                      InMemoryEmbeddingStore<TextSegment> embeddingStore) {
        this.documentParserService = documentParserService;
        this.geminiConfig = geminiConfig;
        this.embeddingStore = embeddingStore;
    }

    public UploadResponseDto uploadAndEmbed(MultipartFile file, String apiKey) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            fileName = "document_" + System.currentTimeMillis();
        }

        // 1. Phân tích file (PDF, Docx, TXT) thành các chunk kèm thông tin section, page
        List<DocumentChunk> chunks = documentParserService.parseDocument(file);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Không thể trích xuất nội dung từ file hoặc file rỗng!");
        }

        // 2. Chuyển đổi thành LangChain4j TextSegment kèm Metadata
        List<TextSegment> segments = new ArrayList<>();
        Set<String> detectedSections = new LinkedHashSet<>();
        int maxPage = 1;

        for (DocumentChunk chunk : chunks) {
            Metadata metadata = new Metadata();
            metadata.put("file_name", chunk.getFileName());
            metadata.put("page_number", chunk.getPageNumber());
            metadata.put("section", chunk.getSection());

            if (chunk.getPageNumber() > maxPage) {
                maxPage = chunk.getPageNumber();
            }
            if (chunk.getSection() != null && !chunk.getSection().isBlank()) {
                detectedSections.add(chunk.getSection());
            }

            segments.add(TextSegment.from(chunk.getText(), metadata));
        }

        // 3. Lấy Embedding Model và tính vector embeddings
        log.info("Bắt đầu sinh embedding cho {} chunks của file '{}'...", segments.size(), fileName);
        EmbeddingModel embeddingModel = geminiConfig.getEmbeddingModel(apiKey);
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        // 4. Lưu vector và text segment trực tiếp vào RAM (InMemoryEmbeddingStore)
        for (int i = 0; i < segments.size(); i++) {
            embeddingStore.add(embeddings.get(i), segments.get(i));
        }

        uploadedDocuments.add(fileName);
        log.info("Đã lưu {} vectors vào RAM thành công!", embeddings.size());

        return UploadResponseDto.builder()
                .fileName(fileName)
                .totalChunks(segments.size())
                .totalPages(maxPage)
                .detectedSections(new ArrayList<>(detectedSections))
                .message("Tải lên và embedding thành công! Đã lưu " + segments.size() + " vectors vào RAM.")
                .build();
    }

    public ChatResponseDto chat(String question, String apiKey) {
        if (embeddingStore.isEmpty()) {
            return ChatResponseDto.builder()
                    .answer("⚠️ **Bộ nhớ RAM hiện chưa có tài liệu nào!**\n\nVui lòng tải lên file tài liệu (PDF hoặc Word) ở bảng bên trái và nhấn **\"Embedding & Lưu vào RAM\"** trước khi đặt câu hỏi nhé.")
                    .citations(Collections.emptyList())
                    .retrievedChunksCount(0)
                    .build();
        }

        // 1. Tính embedding cho câu hỏi
        EmbeddingModel embeddingModel = geminiConfig.getEmbeddingModel(apiKey);
        Embedding questionEmbedding = embeddingModel.embed(question).content();

        // 2. Tìm kiếm các vector gần nhất trong RAM
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding)
                .maxResults(4)
                .minScore(0.3)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

        // Nếu điểm tương đồng > 0.3 không có kết quả, lấy top 3 gần nhất không lọc minScore
        if (matches.isEmpty()) {
            EmbeddingSearchRequest fallbackRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(questionEmbedding)
                    .maxResults(3)
                    .minScore(0.0)
                    .build();
            matches = embeddingStore.search(fallbackRequest).matches();
        }

        // 3. Chuẩn bị context trích dẫn
        StringBuilder contextBuilder = new StringBuilder();
        List<CitationDto> citations = new ArrayList<>();

        for (EmbeddingMatch<TextSegment> match : matches) {
            TextSegment segment = match.embedded();
            String docName = segment.metadata().getString("file_name");
            Integer pageNum = segment.metadata().getInteger("page_number");
            String section = segment.metadata().getString("section");
            String text = segment.text();

            contextBuilder.append(String.format("""
                    ---
                    [NGUỒN DỮ LIỆU TRONG RAM]
                    - Tên file: %s
                    - Section / Mục: %s
                    - Trang: %d
                    - Nội dung gốc:
                    %s
                    """,
                    docName != null ? docName : "Không rõ",
                    section != null ? section : "Không rõ",
                    pageNum != null ? pageNum : 1,
                    text
            ));

            String snippet = text.length() > 220 ? text.substring(0, 220) + "..." : text;
            citations.add(CitationDto.builder()
                    .fileName(docName)
                    .section(section)
                    .pageNumber(pageNum != null ? pageNum : 1)
                    .snippet(snippet)
                    .score(match.score())
                    .build());
        }

        // 4. Xây dựng prompt RAG chuyên nghiệp, trau chuốt, tự nhiên, rành mạch
        String prompt = String.format("""
                Bạn là một Trợ lý AI chuyên gia phân tích tài liệu và giải đáp thông tin (RAG Document Expert).
                Nhiệm vụ của bạn là đọc kỹ các đoạn trích từ tài liệu được nạp trong bộ nhớ RAM dưới đây và trả lời câu hỏi của người dùng một cách mạch lạc, dễ hiểu, tự nhiên và chuyên nghiệp.

                === CÁC ĐOẠN TRÍCH TỪ TÀI LIỆU TRONG RAM ===
                %s
                ===========================================

                CÂU HỎI CỦA NGƯỜI DÙNG:
                "%s"

                HƯỚNG DẪN TRẢ LỜI (BẮT BUỘC TUÂN THỦ ĐỂ CÂU TRẢ LỜI DỄ HIỂU VÀ HAY NHẤT):
                1. **Văn phong**: Diễn đạt bằng tiếng Việt tự nhiên, ấm áp, rành mạch, khoa học và tránh dịch máy thô cứng.
                2. **Bố cục trình bày**:
                   - Đi thẳng vào nội dung cốt lõi của câu hỏi để người đọc nắm bắt ngay.
                   - Nếu câu trả lời có nhiều ý hoặc các bước, hãy dùng gạch đầu dòng (bullet points) rõ ràng, in đậm từ khóa quan trọng.
                   - Giải thích ngắn gọn thuật ngữ nếu cần để câu trả lời dễ tiếp thu nhất.
                3. **YÊU CẦU ĐẶC BIỆT VỀ TRÍCH DẪN SECTION (QUAN TRỌNG ĐỂ CHẤM ĐIỂM)**:
                   - Trong câu trả lời, hãy nêu rõ thông tin này được lấy từ **Section nào** (hoặc Mục/Chương nào, Trang mấy).
                   - Ở cuối câu trả lời, luôn có một khối trích dẫn rõ ràng theo định dạng:
                     > 📍 **Vị trí trích dẫn:** [Tên Section/Mục] (Trang X, File: [Tên file])
                4. **Xử lý ngoài phạm vi**: Nếu thông tin hoàn toàn không xuất hiện trong các đoạn trích trên, hãy giải thích lịch sự, nhẹ nhàng rằng tài liệu hiện tại chưa đề cập đến nội dung này, không tự bịa đặt dữ liệu ngoài tài liệu.
                """,
                contextBuilder,
                question
        );

        // 5. Gửi prompt cho Gemini Chat Model
        ChatModel chatModel = geminiConfig.getChatModel(apiKey);
        String answer = chatModel.chat(prompt);

        return ChatResponseDto.builder()
                .answer(answer)
                .citations(citations)
                .retrievedChunksCount(citations.size())
                .build();
    }

    public MemoryStatusDto getStatus() {
        return MemoryStatusDto.builder()
                .totalDocuments(uploadedDocuments.size())
                .totalVectorsInRam(embeddingStore.size())
                .documentNames(new ArrayList<>(uploadedDocuments))
                .build();
    }

    public void clearAll() {
        embeddingStore.removeAll();
        uploadedDocuments.clear();
        log.info("Đã xóa toàn bộ vector và tài liệu trong RAM.");
    }
}
