package com.rag.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponseDto {
    private String fileName;
    private int totalChunks;
    private int totalPages;
    private List<String> detectedSections;
    private String message;
}
