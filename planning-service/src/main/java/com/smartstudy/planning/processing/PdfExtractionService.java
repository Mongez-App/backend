package com.smartstudy.planning.processing;

import com.smartstudy.planning.config.GeminiProperties;
import com.smartstudy.planning.processing.model.PageContent;
import com.smartstudy.shared.logging.LoggerFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extracts text from PDF files using PDFBox, with Gemini Vision OCR fallback
 * for pages that contain very little extractable text (e.g. scanned pages).
 */
@Service
public class PdfExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractionService.class);

    /**
     * Minimum number of non-whitespace characters a page must have to be
     * considered text-extractable. Below this threshold, OCR fallback is used.
     */
    private static final int MIN_TEXT_CHARS = 50;

    /**
     * DPI used for rendering PDF pages to images for OCR.
     */
    private static final float OCR_RENDER_DPI = 200f;

    // Heading detection patterns
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+.+");
    private static final Pattern NUMBERED_HEADING = Pattern.compile("^\\d+(\\.\\d+)*\\.?\\s+[A-Z].+");
    private static final Pattern ALL_CAPS_HEADING = Pattern.compile("^[A-Z][A-Z\\s]{4,}$");

    private final RestClient geminiRestClient;
    private final GeminiProperties geminiProps;

    public PdfExtractionService(RestClient geminiRestClient, GeminiProperties geminiProps) {
        this.geminiRestClient = geminiRestClient;
        this.geminiProps = geminiProps;
    }

    /**
     * Extract text content from every page of the PDF at the given path.
     *
     * @param pdfPath absolute path to the PDF file
     * @return ordered list of page contents
     */
    public List<PageContent> extract(Path pdfPath) throws IOException {
        log.info("Starting PDF extraction: {}", pdfPath.getFileName());

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            int totalPages = document.getNumberOfPages();
            log.info("PDF has {} pages", totalPages);

            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);
            List<PageContent> pages = new ArrayList<>(totalPages);

            for (int pageIdx = 0; pageIdx < totalPages; pageIdx++) {
                int pageNum = pageIdx + 1;

                // Extract text with PDFBox
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String rawText = stripper.getText(document).trim();

                long nonWhitespace = rawText.chars().filter(c -> !Character.isWhitespace(c)).count();

                if (nonWhitespace >= MIN_TEXT_CHARS) {
                    // Text extraction successful
                    List<String> headings = detectHeadings(rawText);
                    pages.add(new PageContent(pageNum, rawText, false, headings));
                    log.debug("Page {}: extracted {} chars (text)", pageNum, rawText.length());
                } else {
                    // Fallback: render page to image and send to Gemini Vision
                    log.info("Page {}: sparse text ({} chars), using OCR fallback", pageNum, nonWhitespace);
                    String ocrText = ocrPageWithVision(renderer, pageIdx);
                    if (ocrText != null && !ocrText.isBlank()) {
                        List<String> headings = detectHeadings(ocrText);
                        pages.add(new PageContent(pageNum, ocrText, true, headings));
                    } else {
                        // Even OCR produced nothing – still record the page
                        pages.add(new PageContent(pageNum, rawText, false, List.of()));
                        log.warn("Page {}: OCR fallback produced no text", pageNum);
                    }
                }
            }

            log.info("PDF extraction complete: {} pages processed", pages.size());
            return pages;
        }
    }

    /**
     * Render a PDF page to an image, encode as base64 PNG, and send to Gemini Vision
     * for OCR text extraction.
     */
    private String ocrPageWithVision(PDFRenderer renderer, int pageIndex) {
        try {
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, OCR_RENDER_DPI);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());

            String visionModel = geminiProps.vision().model();
            String url = "/models/" + visionModel + ":generateContent";

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(
                                    Map.of(
                                            "inline_data", Map.of(
                                                    "mime_type", "image/png",
                                                    "data", base64Image
                                            )
                                    ),
                                    Map.of("text", "Extract all text from this image. " +
                                            "Preserve the document structure including headings, " +
                                            "paragraphs, and lists. Return only the extracted text, " +
                                            "no commentary.")
                            )
                    )),
                    "generationConfig", Map.of(
                            "temperature", 0.1,
                            "maxOutputTokens", 8192
                    )
            );

            Map<String, Object> response = geminiRestClient.post()
                    .uri(url)
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            return extractTextFromGeminiResponse(response);

        } catch (Exception e) {
            log.warn("OCR fallback failed for page {}: {}", pageIndex + 1, e.getMessage());
            return null;
        }
    }

    /**
     * Parse text content from a Gemini API generateContent response.
     */
    @SuppressWarnings("unchecked")
    private String extractTextFromGeminiResponse(Map<String, Object> response) {
        if (response == null) return null;

        List<Map<String, Object>> candidates =
                (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) return null;

        Map<String, Object> content =
                (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) return null;

        List<Map<String, Object>> parts =
                (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) return null;

        return parts.stream()
                .map(p -> (String) p.get("text"))
                .filter(t -> t != null)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Detect heading lines in extracted text using common patterns:
     * - Markdown headings (# Heading)
     * - Numbered headings (1.2 Title)
     * - ALL CAPS lines (at least 5 uppercase chars)
     */
    private List<String> detectHeadings(String text) {
        return Arrays.stream(text.split("\\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line ->
                        MARKDOWN_HEADING.matcher(line).matches() ||
                                NUMBERED_HEADING.matcher(line).matches() ||
                                ALL_CAPS_HEADING.matcher(line).matches())
                .toList();
    }
}
