package com.smartstudy.planning.processing;

import com.smartstudy.planning.config.GeminiProperties;
import com.smartstudy.planning.processing.model.DocumentChunk;
import com.smartstudy.planning.processing.model.EmbeddedChunk;
import com.smartstudy.shared.logging.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates embeddings using Gemini text-embedding-004 via the REST API.
 * <p>
 * Uses batch embedding for efficiency (up to 100 texts per request) and
 * supports both RETRIEVAL_DOCUMENT (for indexing) and RETRIEVAL_QUERY (for search).
 * </p>
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final RestClient geminiRestClient;
    private final GeminiProperties geminiProps;

    public EmbeddingService(RestClient geminiRestClient, GeminiProperties geminiProps) {
        this.geminiRestClient = geminiRestClient;
        this.geminiProps = geminiProps;
    }

    /**
     * Generate embeddings for a list of document chunks (RETRIEVAL_DOCUMENT task type).
     *
     * @param chunks the document chunks to embed
     * @return list of embedded chunks with their vectors
     */
    public List<EmbeddedChunk> embedChunks(List<DocumentChunk> chunks) {
        log.info("Embedding {} chunks in batches of {}", chunks.size(), geminiProps.embedding().batchSize());

        List<EmbeddedChunk> results = new ArrayList<>(chunks.size());
        int batchSize = geminiProps.embedding().batchSize();

        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, chunks.size());
            List<DocumentChunk> batch = chunks.subList(i, end);

            log.debug("Processing embedding batch {}-{} of {}", i + 1, end, chunks.size());

            List<float[]> vectors = batchEmbed(
                    batch.stream().map(DocumentChunk::text).toList(),
                    "RETRIEVAL_DOCUMENT");

            if (vectors.size() != batch.size()) {
                throw new RuntimeException(
                        "Embedding count mismatch: expected " + batch.size() +
                                " but got " + vectors.size());
            }

            for (int j = 0; j < batch.size(); j++) {
                results.add(new EmbeddedChunk(batch.get(j), vectors.get(j)));
            }
        }

        log.info("Embedding complete: {} vectors generated", results.size());
        return results;
    }

    /**
     * Generate a single embedding for a search query (RETRIEVAL_QUERY task type).
     *
     * @param query the search query text
     * @return the embedding vector
     */
    public float[] embedQuery(String query) {
        log.debug("Embedding query: {}...", query.substring(0, Math.min(80, query.length())));
        List<float[]> vectors = batchEmbed(List.of(query), "RETRIEVAL_QUERY");
        if (vectors.isEmpty()) {
            throw new RuntimeException("Failed to generate query embedding");
        }
        return vectors.get(0);
    }

    /**
     * Call the Gemini batchEmbedContents API.
     *
     * @param texts    the texts to embed
     * @param taskType either "RETRIEVAL_DOCUMENT" or "RETRIEVAL_QUERY"
     * @return list of embedding vectors
     */
    @SuppressWarnings("unchecked")
    private List<float[]> batchEmbed(List<String> texts, String taskType) {
        String model = geminiProps.embedding().model();
        String modelPath = "models/" + model;
        String url = "/" + modelPath + ":batchEmbedContents";

        List<Map<String, Object>> requests = texts.stream()
                .map(text -> Map.<String, Object>of(
                        "model", modelPath,
                        "content", Map.of("parts", List.of(Map.of("text", text))),
                        "taskType", taskType
                ))
                .toList();

        Map<String, Object> requestBody = Map.of("requests", requests);

        Map<String, Object> response = geminiRestClient.post()
                .uri(url)
                .body(requestBody)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        if (response == null) {
            throw new RuntimeException("Gemini embedding API returned null response");
        }

        List<Map<String, Object>> embeddings =
                (List<Map<String, Object>>) response.get("embeddings");
        if (embeddings == null) {
            throw new RuntimeException("Gemini embedding API response missing 'embeddings' field");
        }

        return embeddings.stream()
                .map(emb -> {
                    List<Number> values = (List<Number>) emb.get("values");
                    float[] vector = new float[values.size()];
                    for (int i = 0; i < values.size(); i++) {
                        vector[i] = values.get(i).floatValue();
                    }
                    return vector;
                })
                .toList();
    }
}
