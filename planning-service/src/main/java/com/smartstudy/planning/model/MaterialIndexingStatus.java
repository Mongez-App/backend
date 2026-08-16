package com.smartstudy.planning.model;

/**
 * Progress of a material through the RAG indexing pipeline (extract → chunk →
 * embed → Qdrant).
 * <p>
 * Deliberately separate from {@link MaterialStatus}, which tracks the study-task
 * generation agent. The two pipelines run independently on the same material and
 * previously arbitrated through the single {@code status} field, so whichever
 * finished first decided the other's fate — in practice the agent always won,
 * committing READY before the indexing poller could ever observe PROCESSING, and
 * no material was ever indexed.
 * </p>
 */
public enum MaterialIndexingStatus {

    /** Awaiting indexing. A material is only picked up once its file is on disk. */
    PENDING,

    /** Currently being extracted, chunked, embedded and upserted into Qdrant. */
    INDEXING,

    /** Chunks are searchable in Qdrant. */
    INDEXED,

    /** Indexing failed; retried up to {@code processing.max-retries}. */
    FAILED
}
