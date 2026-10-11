package com.rag.chat.controller;

import com.rag.chat.dto.ChatRequestDto;
import com.rag.chat.dto.ChatResponseDto;
import com.rag.chat.dto.MemoryStatusDto;
import com.rag.chat.dto.UploadResponseDto;
import com.rag.chat.service.RagService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/documents/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "apiKey", required = false) String apiKeyParam,
            @RequestHeader(value = "X-Gemini-Api-Key", required = false) String apiKeyHeader
    ) {
        try {
            String apiKey = (apiKeyParam != null && !apiKeyParam.isBlank()) ? apiKeyParam : apiKeyHeader;
            UploadResponseDto response = ragService.uploadAndEmbed(file, apiKey);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Lỗi khi xử lý file"));
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(
            @Valid @RequestBody ChatRequestDto request,
            @RequestHeader(value = "X-Gemini-Api-Key", required = false) String apiKeyHeader
    ) {
        try {
            String apiKey = (request.getApiKey() != null && !request.getApiKey().isBlank())
                    ? request.getApiKey()
                    : apiKeyHeader;
            ChatResponseDto response = ragService.chat(request.getQuestion(), apiKey);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Lỗi khi xử lý chat"));
        }
    }

    @GetMapping("/documents/status")
    public ResponseEntity<MemoryStatusDto> getStatus() {
        return ResponseEntity.ok(ragService.getStatus());
    }

    @DeleteMapping("/documents")
    public ResponseEntity<?> clearMemory() {
        ragService.clearAll();
        return ResponseEntity.ok(Map.of("message", "Đã xóa toàn bộ vector và tài liệu trong bộ nhớ RAM!"));
    }
}
