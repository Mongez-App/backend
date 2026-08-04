package com.smartstudy.planning.ai.tool;

import com.smartstudy.planning.model.Material;
import com.smartstudy.planning.model.MaterialStatus;
import com.smartstudy.planning.repository.MaterialRepository;
import com.smartstudy.planning.service.FileStorageService;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PdfExtractorTool {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractorTool.class);
    private final MaterialRepository materialRepository;
    private final FileStorageService fileStorageService;

    @Tool(name = "extract_study_tasks", description = "Extract raw text from an uploaded PDF material. Input: materialId (UUID string). Returns the full raw text content of the PDF.")
    public String extract(String materialId) {
        UUID id = UUID.fromString(materialId);
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Material not found: " + materialId));

        MaterialStatus status = material.getStatus();
        if (status != MaterialStatus.PROCESSING && status != MaterialStatus.PENDING) {
            throw new IllegalStateException("Material status is " + status + ", expected PROCESSING or PENDING");
        }

        Path filePath = null;
        if (material.getFilePath() != null && !material.getFilePath().isBlank()) {
            filePath = fileStorageService.resolve(material.getFilePath());
        }
        if (filePath == null || Files.notExists(filePath)) {
            filePath = fileStorageService.load(material.getUserId(), material.getCourseId(), material.getId());
        }

        if (Files.notExists(filePath)) {
            throw new IllegalStateException("PDF file not found on filesystem for material: " + materialId + " at path: " + filePath);
        }

        try {
            try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(filePath.toFile())) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                log.info("Extracted {} characters from material {}", text.length(), materialId);
                return text;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to extract text from PDF: " + ex.getMessage(), ex);
        }
    }
}
