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
public class MemoryStatusDto {
    private int totalDocuments;
    private int totalVectorsInRam;
    private List<String> documentNames;
}
