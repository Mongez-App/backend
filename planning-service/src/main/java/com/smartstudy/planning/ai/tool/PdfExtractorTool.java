package com.smartstudy.planning.ai.tool;

import com.smartstudy.planning.model.Material;
import com.smartstudy.planning.repository.MaterialRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;

@Service
@RequiredArgsConstructor
public class PdfExtractorTool {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractorTool.class);
    private static final Path MATERIAL_UPLOAD_DIR = Paths.get("uploads", "materials");
    private final MaterialRepository materialRepository;

    @Tool(name = "extract_study_tasks", description = "Extract raw text from an uploaded PDF material. Input: materialId (UUID string). Returns the full raw text content of the PDF.")
    public String extract(String materialId) {
        UUID id = UUID.fromString(materialId);
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Material not found: " + materialId));

        if (!"uploaded".equals(material.getStatus()) && !"processing".equals(material.getStatus())) {
            throw new IllegalStateException("Material status is " + material.getStatus() + ", expected uploaded or processing");
        }

        Path filePath = MATERIAL_UPLOAD_DIR.resolve(materialId);
        if (Files.notExists(filePath)) {
            throw new IllegalStateException("PDF file not found on filesystem for material: " + materialId);
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
