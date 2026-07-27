package com.smartstudy.planning.processing;

import com.smartstudy.planning.processing.model.DocumentChunk;
import com.smartstudy.planning.processing.model.PageContent;
import com.smartstudy.shared.logging.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Heading-aware text chunking service.
 * <p>
 * Splits extracted page text into semantically coherent chunks using heading boundaries,
 * with configurable target/max/min token sizes and inter-chunk overlap.
 * </p>
 */
@Service
public class ChunkingService {

    private static final Logger log = LoggerFactory.getLogger(ChunkingService.class);

    // Token size parameters (whitespace-split approximation)
    private static final int TARGET_TOKENS = 800;
    private static final int MAX_TOKENS = 1200;
    private static final int MIN_TOKENS = 50;
    private static final int OVERLAP_TOKENS = 100;

    // Heading detection patterns (same as PdfExtractionService)
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+.+");
    private static final Pattern NUMBERED_HEADING = Pattern.compile("^\\d+(\\.\\d+)*\\.?\\s+[A-Z].+");
    private static final Pattern ALL_CAPS_LINE = Pattern.compile("^[A-Z][A-Z\\s]{4,}$");

    // Sentence boundary pattern for fine-grained splitting
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+");

    /**
     * Split a list of extracted page contents into heading-aware document chunks.
     *
     * @param pages the extracted page contents in order
     * @return ordered list of document chunks
     */
    public List<DocumentChunk> chunk(List<PageContent> pages) {
        log.info("Chunking {} pages", pages.size());

        // Step 1: Build sections by splitting at heading boundaries
        List<Section> sections = buildSections(pages);
        log.debug("Identified {} sections", sections.size());

        // Step 2: Split oversized sections and merge tiny ones
        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkOrder = 0;

        for (Section section : sections) {
            int tokenCount = countTokens(section.text);

            if (tokenCount <= MAX_TOKENS) {
                // Section fits in a single chunk
                if (tokenCount < MIN_TOKENS && !chunks.isEmpty()) {
                    // Merge with previous chunk
                    DocumentChunk prev = chunks.remove(chunks.size() - 1);
                    String merged = prev.text() + "\n\n" + section.text;
                    chunks.add(new DocumentChunk(
                            merged, prev.chunkOrder(),
                            prev.pageStart(), section.pageEnd,
                            prev.sectionTitle(),
                            prev.headingHierarchy(),
                            "text",
                            countTokens(merged)));
                } else {
                    chunks.add(new DocumentChunk(
                            prependHeadings(section.text, section.headingHierarchy),
                            chunkOrder++,
                            section.pageStart, section.pageEnd,
                            section.title,
                            section.headingHierarchy,
                            "text",
                            tokenCount));
                }
            } else {
                // Section too large — split on paragraph boundaries first, then sentences
                List<DocumentChunk> subChunks = splitLargeSection(section, chunkOrder);
                chunks.addAll(subChunks);
                chunkOrder += subChunks.size();
            }
        }

        // Step 3: Apply overlap between consecutive chunks
        chunks = applyOverlap(chunks);

        log.info("Chunking complete: {} chunks produced", chunks.size());
        return chunks;
    }

    /**
     * Group pages into sections based on heading boundaries.
     */
    private List<Section> buildSections(List<PageContent> pages) {
        List<Section> sections = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        String currentTitle = "Introduction";
        List<String> currentHierarchy = new ArrayList<>();
        int pageStart = pages.isEmpty() ? 1 : pages.get(0).pageNumber();
        int pageEnd = pageStart;

        for (PageContent page : pages) {
            String[] lines = page.text().split("\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    currentText.append("\n");
                    continue;
                }

                if (isHeading(trimmed)) {
                    // Save current section if non-empty
                    if (!currentText.toString().isBlank()) {
                        sections.add(new Section(
                                currentText.toString().trim(),
                                currentTitle,
                                new ArrayList<>(currentHierarchy),
                                pageStart, pageEnd));
                    }

                    // Start new section
                    currentTitle = cleanHeading(trimmed);
                    updateHierarchy(currentHierarchy, trimmed);
                    currentText = new StringBuilder();
                    pageStart = page.pageNumber();
                }

                currentText.append(line).append("\n");
                pageEnd = page.pageNumber();
            }
        }

        // Add final section
        if (!currentText.toString().isBlank()) {
            sections.add(new Section(
                    currentText.toString().trim(),
                    currentTitle,
                    new ArrayList<>(currentHierarchy),
                    pageStart, pageEnd));
        }

        return sections;
    }

    /**
     * Split a section that exceeds MAX_TOKENS into sub-chunks.
     * First splits on paragraph boundaries (\n\n), then on sentence boundaries.
     */
    private List<DocumentChunk> splitLargeSection(Section section, int startOrder) {
        List<DocumentChunk> results = new ArrayList<>();
        String headingPrefix = buildHeadingPrefix(section.headingHierarchy);

        // Split into paragraphs
        String[] paragraphs = section.text.split("\\n\\n+");

        StringBuilder buffer = new StringBuilder();
        int bufferTokens = 0;
        int order = startOrder;

        for (String para : paragraphs) {
            int paraTokens = countTokens(para);

            if (paraTokens > MAX_TOKENS) {
                // Paragraph itself is too large — split on sentences
                flushBuffer(buffer, bufferTokens, results, section, order, headingPrefix);
                if (!results.isEmpty() && results.get(results.size() - 1).chunkOrder() >= order) {
                    order = results.get(results.size() - 1).chunkOrder() + 1;
                }

                List<DocumentChunk> sentenceChunks = splitOnSentences(
                        para, section, order, headingPrefix);
                results.addAll(sentenceChunks);
                order += sentenceChunks.size();
                buffer = new StringBuilder();
                bufferTokens = 0;
                continue;
            }

            if (bufferTokens + paraTokens > TARGET_TOKENS && bufferTokens > 0) {
                // Buffer would exceed target — flush
                flushBuffer(buffer, bufferTokens, results, section, order++, headingPrefix);
                buffer = new StringBuilder();
                bufferTokens = 0;
            }

            if (buffer.length() > 0) {
                buffer.append("\n\n");
            }
            buffer.append(para);
            bufferTokens += paraTokens;
        }

        // Flush remaining
        if (bufferTokens > 0) {
            flushBuffer(buffer, bufferTokens, results, section, order, headingPrefix);
        }

        return results;
    }

    /**
     * Split text on sentence boundaries when paragraph-level splitting is insufficient.
     */
    private List<DocumentChunk> splitOnSentences(String text, Section section,
                                                  int startOrder, String headingPrefix) {
        List<DocumentChunk> results = new ArrayList<>();
        String[] sentences = SENTENCE_BOUNDARY.split(text);

        StringBuilder buffer = new StringBuilder();
        int bufferTokens = 0;
        int order = startOrder;

        for (String sentence : sentences) {
            int sentenceTokens = countTokens(sentence);

            if (bufferTokens + sentenceTokens > TARGET_TOKENS && bufferTokens > 0) {
                String chunkText = headingPrefix + buffer.toString().trim();
                results.add(new DocumentChunk(
                        chunkText, order++,
                        section.pageStart, section.pageEnd,
                        section.title,
                        section.headingHierarchy,
                        "text",
                        countTokens(chunkText)));
                buffer = new StringBuilder();
                bufferTokens = 0;
            }

            buffer.append(sentence).append(" ");
            bufferTokens += sentenceTokens;
        }

        if (bufferTokens > 0) {
            String chunkText = headingPrefix + buffer.toString().trim();
            results.add(new DocumentChunk(
                    chunkText, order,
                    section.pageStart, section.pageEnd,
                    section.title,
                    section.headingHierarchy,
                    "text",
                    countTokens(chunkText)));
        }

        return results;
    }

    /**
     * Apply token overlap between consecutive chunks for retrieval continuity.
     */
    private List<DocumentChunk> applyOverlap(List<DocumentChunk> chunks) {
        if (chunks.size() <= 1) return chunks;

        List<DocumentChunk> result = new ArrayList<>(chunks.size());
        result.add(chunks.get(0)); // First chunk stays as-is

        for (int i = 1; i < chunks.size(); i++) {
            DocumentChunk prev = chunks.get(i - 1);
            DocumentChunk curr = chunks.get(i);

            String overlapText = getLastNTokens(prev.text(), OVERLAP_TOKENS);
            if (!overlapText.isEmpty()) {
                String newText = overlapText + "\n\n" + curr.text();
                result.add(new DocumentChunk(
                        newText, curr.chunkOrder(),
                        Math.min(prev.pageEnd(), curr.pageStart()),
                        curr.pageEnd(),
                        curr.sectionTitle(),
                        curr.headingHierarchy(),
                        curr.contentType(),
                        countTokens(newText)));
            } else {
                result.add(curr);
            }
        }

        return result;
    }

    private void flushBuffer(StringBuilder buffer, int tokens, List<DocumentChunk> results,
                             Section section, int order, String headingPrefix) {
        if (tokens == 0) return;
        String chunkText = headingPrefix + buffer.toString().trim();
        results.add(new DocumentChunk(
                chunkText, order,
                section.pageStart, section.pageEnd,
                section.title,
                section.headingHierarchy,
                "text",
                countTokens(chunkText)));
    }

    private boolean isHeading(String line) {
        return MARKDOWN_HEADING.matcher(line).matches()
                || NUMBERED_HEADING.matcher(line).matches()
                || ALL_CAPS_LINE.matcher(line).matches();
    }

    private String cleanHeading(String heading) {
        // Remove markdown # symbols and trim
        return heading.replaceAll("^#+\\s*", "")
                .replaceAll("^\\d+(\\.\\d+)*\\.?\\s*", "")
                .trim();
    }

    private void updateHierarchy(List<String> hierarchy, String heading) {
        int level = detectHeadingLevel(heading);
        // Trim hierarchy to current level and add
        while (hierarchy.size() >= level) {
            hierarchy.remove(hierarchy.size() - 1);
        }
        hierarchy.add(cleanHeading(heading));
    }

    private int detectHeadingLevel(String heading) {
        if (heading.startsWith("#")) {
            int level = 0;
            for (char c : heading.toCharArray()) {
                if (c == '#') level++;
                else break;
            }
            return Math.min(level, 6);
        }
        // Numbered heading: count dots for depth
        if (NUMBERED_HEADING.matcher(heading).matches()) {
            long dots = heading.chars().takeWhile(c -> c != ' ').filter(c -> c == '.').count();
            return (int) (dots + 1);
        }
        // ALL CAPS = top level
        return 1;
    }

    private String prependHeadings(String text, List<String> hierarchy) {
        if (hierarchy.isEmpty()) return text;
        String prefix = buildHeadingPrefix(hierarchy);
        return prefix + text;
    }

    private String buildHeadingPrefix(List<String> hierarchy) {
        if (hierarchy.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hierarchy.size(); i++) {
            sb.append("#".repeat(i + 1)).append(" ").append(hierarchy.get(i)).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Get the last N tokens from text as overlap context.
     */
    private String getLastNTokens(String text, int n) {
        String[] words = text.split("\\s+");
        if (words.length <= n) return text;
        return String.join(" ", Arrays.copyOfRange(words, words.length - n, words.length));
    }

    /**
     * Approximate token count using whitespace splitting.
     */
    static int countTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.split("\\s+").length;
    }

    /**
     * Internal representation of a document section between headings.
     */
    private record Section(
            String text,
            String title,
            List<String> headingHierarchy,
            int pageStart,
            int pageEnd
    ) {
    }
}
