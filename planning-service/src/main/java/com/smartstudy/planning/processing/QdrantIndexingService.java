package com.smartstudy.planning.processing;

import com.smartstudy.planning.config.QdrantProperties;
import com.smartstudy.planning.processing.model.DocumentChunk;
import com.smartstudy.planning.processing.model.EmbeddedChunk;
import com.smartstudy.shared.logging.LoggerFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;
import static io.qdrant.client.ConditionFactory.matchKeyword;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;

/**
 * Manages vector storage and retrieval in Qdrant.
 * <p>
 * Handles collection initialization, chunk indexing (upsert), deletion,
 * and filtered vector similarity search.
 * </p>
 */
@Service
public class QdrantIndexingService {

    private static final Logger log = LoggerFactory.getLogger(QdrantIndexingService.class);

    /**
     * Dimensionality of Gemini text-embedding-004 vectors.
     */
    private static final int VECTOR_DIM = 768;

    /**
     * Timeout for Qdrant operations in seconds.
     */
    private static final int TIMEOUT_SECONDS = 30;

    private final QdrantClient qdrantClient;
    private final QdrantProperties props;

    public QdrantIndexingService(QdrantClient qdrantClient, QdrantProperties props) {
        this.qdrantClient = qdrantClient;
        this.props = props;
    }

    /**
     * Ensure the collection exists with the correct vector configuration.
     */
    @PostConstruct
    public void initCollection() {
        String collection = props.collectionName();
        try {
            boolean exists = qdrantClient.collectionExistsAsync(collection)
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!exists) {
                log.info("Creating Qdrant collection '{}' (dim={}, distance=Cosine)",
                        collection, VECTOR_DIM);
                qdrantClient.createCollectionAsync(
                        collection,
                        VectorParams.newBuilder()
                                .setSize(VECTOR_DIM)
                                .setDistance(Distance.Cosine)
                                .build()
                ).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

                // Create payload indexes for filtered search
                createPayloadIndexes(collection);
                log.info("Collection '{}' created with payload indexes", collection);
            } else {
                log.info("Qdrant collection '{}' already exists", collection);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted during Qdrant collection init", e);
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Could not initialize Qdrant collection '{}' — " +
                    "will retry on first indexing operation: {}", collection, e.getMessage());
        }
    }

    /**
     * Index embedded chunks into Qdrant.
     *
     * @param materialId the material these chunks belong to
     * @param userId     the owning user
     * @param courseId   the course this material is part of
     * @param chunks     the embedded chunks to upsert
     */
    public void indexChunks(UUID materialId, String userId, UUID courseId,
                            List<EmbeddedChunk> chunks) throws Exception {
        log.info("Indexing {} chunks for material {}", chunks.size(), materialId);

        List<PointStruct> points = new ArrayList<>(chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            EmbeddedChunk ec = chunks.get(i);
            DocumentChunk dc = ec.chunk();

            // Generate a deterministic point ID based on materialId + chunk order
            UUID pointId = UUID.nameUUIDFromBytes(
                    (materialId + "-" + dc.chunkOrder()).getBytes());

            Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payload = new HashMap<>();
            payload.put("user_id", value(userId));
            payload.put("course_id", value(courseId.toString()));
            payload.put("material_id", value(materialId.toString()));
            payload.put("chunk_order", value((long) dc.chunkOrder()));
            payload.put("page_start", value((long) dc.pageStart()));
            payload.put("page_end", value((long) dc.pageEnd()));
            payload.put("section_title", value(dc.sectionTitle() != null ? dc.sectionTitle() : ""));
            payload.put("content_type", value(dc.contentType() != null ? dc.contentType() : "text"));
            payload.put("token_count", value((long) dc.tokenCount()));
            payload.put("text", value(dc.text()));

            points.add(PointStruct.newBuilder()
                    .setId(id(pointId))
                    .setVectors(vectors(ec.vector()))
                    .putAllPayload(payload)
                    .build());
        }

        qdrantClient.upsertAsync(props.collectionName(), points)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.info("Indexed {} points for material {}", points.size(), materialId);
    }

    /**
     * Delete all indexed points for a specific material.
     */
    public void deleteByMaterialId(UUID materialId) {
        try {
            Filter filter = Filter.newBuilder()
                    .addMust(matchKeyword("material_id", materialId.toString()))
                    .build();

            qdrantClient.deleteAsync(
                    props.collectionName(),
                    filter
            ).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("Deleted Qdrant points for material {}", materialId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while deleting points for material {}", materialId, e);
        } catch (ExecutionException | TimeoutException e) {
            log.error("Failed to delete Qdrant points for material {}", materialId, e);
        }
    }

    /**
     * Perform a filtered vector similarity search.
     *
     * @param userId      filter to this user's materials
     * @param courseId     filter to this course
     * @param queryVector the search query embedding
     * @param limit       max number of results
     * @return list of scored points with payload
     */
    public List<ScoredPoint> search(String userId, UUID courseId,
                                     float[] queryVector, int limit) throws Exception {
        Filter filter = Filter.newBuilder()
                .addMust(matchKeyword("user_id", userId))
                .addMust(matchKeyword("course_id", courseId.toString()))
                .build();

        return qdrantClient.searchAsync(
                SearchPoints.newBuilder()
                        .setCollectionName(props.collectionName())
                        .addAllVector(toFloatList(queryVector))
                        .setFilter(filter)
                        .setLimit(limit)
                        .setWithPayload(enable(true))
                        .build()
        ).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void createPayloadIndexes(String collection) {
        try {
            qdrantClient.createPayloadIndexAsync(collection, "user_id",
                            io.qdrant.client.grpc.Collections.PayloadSchemaType.Keyword, null, null, null, null)
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            qdrantClient.createPayloadIndexAsync(collection, "course_id",
                            io.qdrant.client.grpc.Collections.PayloadSchemaType.Keyword, null, null, null, null)
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            qdrantClient.createPayloadIndexAsync(collection, "material_id",
                            io.qdrant.client.grpc.Collections.PayloadSchemaType.Keyword, null, null, null, null)
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            qdrantClient.createPayloadIndexAsync(collection, "content_type",
                            io.qdrant.client.grpc.Collections.PayloadSchemaType.Keyword, null, null, null, null)
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to create payload indexes (may already exist): {}", e.getMessage());
        }
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) {
            list.add(v);
        }
        return list;
    }
}
