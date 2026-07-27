package com.smartstudy.planning.processing.model;

import java.util.List;

/**
 * A text chunk produced by heading-aware splitting, ready for embedding.
 *
 * @param text             the chunk text (with heading hierarchy prepended)
 * @param chunkOrder       0-based ordering within the document
 * @param pageStart        first page this chunk spans
 * @param pageEnd          last page this chunk spans
 * @param sectionTitle     the immediate heading / section title
 * @param headingHierarchy ordered list of headings from root to current section
 * @param contentType      e.g. "text", "table", "code"
 * @param tokenCount       approximate token count (whitespace-split)
 */
public record DocumentChunk(
        String text,
        int chunkOrder,
        int pageStart,
        int pageEnd,
        String sectionTitle,
        List<String> headingHierarchy,
        String contentType,
        int tokenCount
) {
}
