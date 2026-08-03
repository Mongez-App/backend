package com.smartstudy.planning.processing;

import com.smartstudy.planning.config.ProcessingProperties;
import com.smartstudy.planning.model.Material;
import com.smartstudy.planning.model.MaterialStatus;
import com.smartstudy.planning.repository.MaterialRepository;
import com.smartstudy.shared.logging.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

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
     * Primary poller: look for the oldest material in PROCESSING status and process it.
     * Runs with a configurable fixed delay (default 5 seconds).
     */
    @Scheduled(fixedDelayString = "${processing.poll-interval-ms:5000}")
    @Transactional
    public void pollForProcessing() {
        Optional<Material> opt = materialRepository
                .findFirstByStatusOrderByUploadedAtAsc(MaterialStatus.PROCESSING);

        if (opt.isEmpty()) {
            return; // Nothing to process
        }

        Material material = opt.get();
        log.info("Picked up material {} ({}) for processing",
                material.getId(), material.getName());

        material.setProcessingStartedAt(Instant.now());
        materialRepository.save(material);

        try {
            processingService.process(material);

            // Success
            material.setStatus(MaterialStatus.READY);
            material.setProcessedAt(Instant.now());
            material.setErrorMessage(null);
            materialRepository.save(material);

            log.info("Material {} processed successfully → READY", material.getId());

        } catch (Exception e) {
            // Failure
            material.setStatus(MaterialStatus.FAILED);
            material.setErrorMessage(truncateMessage(e.getMessage(), 2000));
            material.setRetryCount(material.getRetryCount() + 1);
            materialRepository.save(material);

            log.error("Material {} processing failed (retry {}/{}): {}",
                    material.getId(),
                    material.getRetryCount(),
                    processingProps.maxRetries(),
                    e.getMessage());
        }
    }

    /**
     * Retry poller: look for FAILED materials that haven't exceeded the retry limit
     * and re-queue them to PROCESSING status.
     * Runs with a configurable fixed delay (default 30 minutes).
     */
    @Scheduled(fixedDelayString = "${processing.retry-interval-ms:1800000}")
    @Transactional
    public void retryFailedMaterials() {
        Optional<Material> opt = materialRepository
                .findFirstByStatusAndRetryCountLessThanOrderByUploadedAtAsc(
                        MaterialStatus.FAILED, processingProps.maxRetries());

        if (opt.isEmpty()) {
            return; // No retryable materials
        }

        Material material = opt.get();
        log.info("Re-queuing FAILED material {} for retry (attempt {}/{})",
                material.getId(),
                material.getRetryCount() + 1,
                processingProps.maxRetries());

        material.setStatus(MaterialStatus.PROCESSING);
        material.setErrorMessage(null);
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
