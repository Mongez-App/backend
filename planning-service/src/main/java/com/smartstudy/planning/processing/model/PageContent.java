package com.smartstudy.planning.processing.model;

import java.util.List;

/**
 * Represents extracted text content from a single PDF page.
 *
 * @param pageNumber 1-based page number
 * @param text       extracted text content
 * @param isOcr      true if text was obtained via Gemini Vision OCR (image-based page)
 * @param headings   detected heading lines on this page
 */
public record PageContent(
        int pageNumber,
        String text,
        boolean isOcr,
        List<String> headings
) {
}
