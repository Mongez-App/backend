package com.smartstudy.planning.processing;

import com.smartstudy.planning.config.ProcessingProperties;
import com.smartstudy.planning.model.Material;
import com.smartstudy.planning.model.MaterialIndexingStatus;
import com.smartstudy.planning.repository.MaterialRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Background scheduler that polls for materials to process and retries failed ones.
 * <p>
 * Two scheduled methods:
 * <ul>
 *   <li><b>Primary poller</b>: picks up PROCESSING materials and runs them through the pipeline</li>
 *   <li><b>Retry poller</b>: re-queues FAILED materials (up to maxRetries) for reprocessing</li>
 * </ul>
 * </p>
 */
@Service
public class ScheduledProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledProcessingService.class);

    private final MaterialRepository materialRepository;
    private final MaterialProcessingService processingService;
    private final ProcessingProperties processingProps;

    public ScheduledProcessingService(MaterialRepository materialRepository,
                                      MaterialProcessingService processingService,
                                      ProcessingProperties processingProps) {
        this.materialRepository = materialRepository;
        this.processingService = processingService;
        this.processingProps = processingProps;
    }

    /**
     * Primary poller: index the oldest material awaiting it.
     * Runs with a configurable fixed delay (default 5 seconds).
     * <p>
     * Keyed on {@code indexingStatus}, never on {@code status}. The latter belongs
     * to the task-generation agent, which sets PROCESSING and then READY inside a
     * single upload transaction — so PROCESSING was never visible to this poller
     * and nothing was ever indexed.
     * </p>
     */
    @Scheduled(fixedDelayString = "${processing.poll-interval-ms:5000}")
    @Transactional
    public void pollForProcessing() {
        List<Material> awaiting = materialRepository.findAwaitingIndexing(
                MaterialIndexingStatus.PENDING, PageRequest.of(0, 1));

        if (awaiting.isEmpty()) {
            return; // Nothing to index
        }

        Material material = awaiting.get(0);
        log.info("Picked up material {} ({}) for indexing",
                material.getId(), material.getName());

        material.setIndexingStatus(MaterialIndexingStatus.INDEXING);
        materialRepository.save(material);

        try {
            processingService.process(material);

            // Success
            material.setIndexingStatus(MaterialIndexingStatus.INDEXED);
            material.setIndexedAt(Instant.now());
            material.setIndexingErrorMessage(null);
            materialRepository.save(material);

            log.info("Material {} indexed successfully → INDEXED", material.getId());

        } catch (Exception e) {
            // Failure
            material.setIndexingStatus(MaterialIndexingStatus.FAILED);
            material.setIndexingErrorMessage(truncateMessage(e.getMessage(), 2000));
            material.setRetryCount(material.getRetryCount() + 1);
            materialRepository.save(material);

            log.error("Material {} indexing failed (retry {}/{}): {}",
                    material.getId(),
                    material.getRetryCount(),
                    processingProps.maxRetries(),
                    e.getMessage());
        }
    }

    /**
     * Retry poller: re-queue materials whose indexing failed and that still have
     * retries left. Runs with a configurable fixed delay (default 30 minutes).
     */
    @Scheduled(fixedDelayString = "${processing.retry-interval-ms:1800000}")
    @Transactional
    public void retryFailedMaterials() {
        List<Material> retryable = materialRepository.findRetryableIndexing(
                MaterialIndexingStatus.FAILED, processingProps.maxRetries(), PageRequest.of(0, 1));

        if (retryable.isEmpty()) {
            return; // No retryable materials
        }

        Material material = retryable.get(0);
        log.info("Re-queuing material {} for indexing retry (attempt {}/{})",
                material.getId(),
                material.getRetryCount() + 1,
                processingProps.maxRetries());

        material.setIndexingStatus(MaterialIndexingStatus.PENDING);
        material.setIndexingErrorMessage(null);
        materialRepository.save(material);
    }

    /**
     * Truncate error messages to avoid oversized DB columns.
     */
    private String truncateMessage(String message, int maxLength) {
        if (message == null) return "Unknown error";
        return message.length() <= maxLength ? message : message.substring(0, maxLength) + "...";
    }
}
