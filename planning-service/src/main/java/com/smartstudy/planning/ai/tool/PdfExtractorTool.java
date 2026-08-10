package com.smartstudy.planning.ai.tool;

import com.smartstudy.planning.ai.model.ExtractedTask;
import com.smartstudy.planning.ai.model.TaskExtractionResult;
import com.smartstudy.planning.model.Material;
import com.smartstudy.planning.model.MaterialStatus;
import com.smartstudy.planning.repository.MaterialRepository;
import com.smartstudy.planning.service.FileStorageService;
import com.smartstudy.shared.logging.LoggerFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PdfExtractorTool {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractorTool.class);
    private static final String SYSTEM_PROMPT = """
            You are a study planning assistant. Given raw PDF text from a course material, extract a list of ordered study tasks.
            Return ONLY a valid JSON array of objects with fields: title (String), estimatedMinutes (int), sequenceOrder (int), notes (String nullable, default null).
            Do not include any markdown fences. Example: [{"title":"Chapter 1","estimatedMinutes":45,"sequenceOrder":1,"notes":null}]
            Tasks must be in strict study order. Do not compress or skip sections.
            """;
    private final MaterialRepository materialRepository;
    private final FileStorageService fileStorageService;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    @Tool(name = "extract_and_parse_tasks", description = "Extract text from an uploaded PDF material and parse it into structured study tasks. Input: materialId (UUID string). Returns a compact summary of extracted tasks.")
    public TaskExtractionResult extractAndParseTasks(String materialId) {
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

        String rawText;
        try {
            try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(filePath.toFile())) {
                PDFTextStripper stripper = new PDFTextStripper();
                rawText = stripper.getText(document);
                log.info("Extracted {} characters from material {}", rawText.length(), materialId);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to extract text from PDF: " + ex.getMessage(), ex);
        }

        List<ExtractedTask> tasks = parseTasksFromText(rawText, id);

        String summary = tasks.stream()
                .map(t -> t.title() + "(" + t.estimatedMinutes() + "m)")
                .reduce((a, b) -> a + ", " + b)
                .orElse("No tasks extracted");

        return new TaskExtractionResult("Extracted " + tasks.size() + " tasks: " + summary, tasks);
    }

    private List<ExtractedTask> parseTasksFromText(String rawText, UUID materialId) {
        String prompt = "Extract study tasks from the following material text:\n\n" + rawText;

        try {
            String response = chatClientBuilder.build().prompt()
                    .system(SYSTEM_PROMPT)
                    .user(prompt)
                    .call()
                    .content();

            response = response.trim();
            if (response.startsWith("```json")) {
                response = response.substring(7);
            } else if (response.startsWith("```")) {
                response = response.substring(3);
            }
            if (response.endsWith("```")) {
                response = response.substring(0, response.length() - 3);
            }
            response = response.trim();

            List<ExtractedTask> tasks = objectMapper.readValue(response, new TypeReference<List<ExtractedTask>>() {});

            if (tasks.isEmpty()) {
                throw new IllegalStateException("LLM returned empty task list for material " + materialId);
            }

            tasks.sort((a, b) -> Integer.compare(a.sequenceOrder(), b.sequenceOrder()));
            return tasks;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to extract tasks from PDF text: " + ex.getMessage(), ex);
        }
    }
}
