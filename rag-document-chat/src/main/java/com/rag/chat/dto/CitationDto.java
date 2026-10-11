package com.rag.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitationDto {
    private String fileName;
    private String section;
    private int pageNumber;
    private String snippet;
    private Double score;
}
