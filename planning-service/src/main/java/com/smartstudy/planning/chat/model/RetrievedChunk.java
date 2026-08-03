package com.smartstudy.planning.chat.model;

/**
 * Internal DTO representing a retrieved chunk from Qdrant vector search.
 *
 * @param text         the chunk text content
 * @param sectionTitle the section title from the source material
 * @param pageStart    start page in the source document
 * @param pageEnd      end page in the source document
 * @param score        similarity score from Qdrant
 */
public record RetrievedChunk(
    String text,
    String sectionTitle,
    int pageStart,
    int pageEnd,
    float score
) {}
