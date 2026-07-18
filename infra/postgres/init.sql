-- One database per service (service-based architecture: each service owns its data).
CREATE DATABASE identity_db;
CREATE DATABASE planning_db;
CREATE DATABASE ai_db;
CREATE DATABASE notification_db;

-- pgvector is only needed by the ai-worker's vector store.
\c ai_db
CREATE EXTENSION IF NOT EXISTS vector;
