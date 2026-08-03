package com.smartstudy.planning.processing.model;

/**
 * A document chunk paired with its embedding vector (768-dimensional from Gemini text-embedding-004).
 *
 * @param chunk  the source document chunk
 * @param vector the embedding vector
 */
public record EmbeddedChunk(
        DocumentChunk chunk,
        float[] vector
) {
}
