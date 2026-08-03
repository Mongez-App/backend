package com.smartstudy.planning.processing;

import com.smartstudy.planning.model.Material;
import com.smartstudy.planning.processing.model.DocumentChunk;
import com.smartstudy.planning.processing.model.EmbeddedChunk;
import com.smartstudy.planning.processing.model.PageContent;
import com.smartstudy.planning.service.FileStorageService;
import com.smartstudy.shared.logging.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * Orchestrates the full AI material processing pipeline for a single material:
 * <ol>
 *   <li>Read PDF from disk (FileStorageService)</li>
 *   <li>Extract text from all pages (PdfExtractionService)</li>
 *   <li>Split into chunks (ChunkingService)</li>
 *   <li>Generate embeddings (EmbeddingService)</li>
 *   <li>Index in Qdrant (QdrantIndexingService)</li>
 * </ol>
 * <p>
 * On failure, cleans up any partial Qdrant data before propagating the exception
 * so the caller (ScheduledProcessingService) can handle status transitions.
 * </p>
 */
@Service
public class MaterialProcessingService {

    private static final Logger log = LoggerFactory.getLogger(MaterialProcessingService.class);

    private final FileStorageService fileStorageService;
    private final PdfExtractionService pdfExtractionService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final QdrantIndexingService qdrantIndexingService;

    public MaterialProcessingService(FileStorageService fileStorageService,
                                     PdfExtractionService pdfExtractionService,
                                     ChunkingService chunkingService,
                                     EmbeddingService embeddingService,
                                     QdrantIndexingService qdrantIndexingService) {
        this.fileStorageService = fileStorageService;
        this.pdfExtractionService = pdfExtractionService;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.qdrantIndexingService = qdrantIndexingService;
    }

    /**
     * Process a single material through the full pipeline.
     *
     * @param material the material to process (must have filePath, userId, courseId set)
     * @throws Exception if any pipeline stage fails
     */
    public void process(Material material) throws Exception {
        log.info("Starting processing pipeline for material {} ({})",
                material.getId(), material.getName());

        try {
            // Step 1: Load PDF from disk
            Path pdfPath = fileStorageService.load(
                    material.getUserId(), material.getCourseId(), material.getId());
            log.debug("Step 1/5: PDF located at {}", pdfPath);

            // Step 2: Extract text from all pages
            List<PageContent> pages = pdfExtractionService.extract(pdfPath);
            log.info("Step 2/5: Extracted {} pages ({} OCR)",
                    pages.size(),
                    pages.stream().filter(PageContent::isOcr).count());

            if (pages.isEmpty() || pages.stream().allMatch(p -> p.text().isBlank())) {
                throw new RuntimeException("No text content could be extracted from the PDF");
            }

            // Step 3: Split into chunks
            List<DocumentChunk> chunks = chunkingService.chunk(pages);
            log.info("Step 3/5: Created {} chunks (avg {} tokens)",
                    chunks.size(),
                    chunks.isEmpty() ? 0 :
                            chunks.stream().mapToInt(DocumentChunk::tokenCount).sum() / chunks.size());

            if (chunks.isEmpty()) {
                throw new RuntimeException("Chunking produced no chunks from the extracted text");
            }

            // Step 4: Generate embeddings
            List<EmbeddedChunk> embeddedChunks = embeddingService.embedChunks(chunks);
            log.info("Step 4/5: Generated {} embeddings", embeddedChunks.size());

            // Step 5: Index in Qdrant
            qdrantIndexingService.indexChunks(
                    material.getId(),
                    material.getUserId(),
                    material.getCourseId(),
                    embeddedChunks);
            log.info("Step 5/5: Indexed {} vectors in Qdrant", embeddedChunks.size());

            log.info("Processing pipeline complete for material {}", material.getId());

        } catch (Exception e) {
            log.error("Processing pipeline failed for material {}: {}",
                    material.getId(), e.getMessage());

            // Clean up any partial Qdrant data
            try {
                qdrantIndexingService.deleteByMaterialId(material.getId());
                log.info("Cleaned up partial Qdrant data for material {}", material.getId());
            } catch (Exception cleanup) {
                log.warn("Failed to clean up Qdrant data for material {}: {}",
                        material.getId(), cleanup.getMessage());
            }

            throw e;
        }
    }
}
