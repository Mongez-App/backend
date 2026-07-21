-- One database per service (service-based architecture: each service owns its data).
CREATE DATABASE identity_db;
CREATE DATABASE planning_db;

-- pgvector is only needed by the ai-worker's vector store.
\c planning_db
CREATE EXTENSION IF NOT EXISTS vector;