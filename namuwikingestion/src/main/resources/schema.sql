CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS namuwiki_doc (
                                            id         BIGSERIAL PRIMARY KEY,
                                            title      TEXT NOT NULL,
                                            content    TEXT NOT NULL,
                                            embedding  vector(384),
    namespace  TEXT,
    contributors TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS namuwiki_doc_embedding_idx ON namuwiki_doc
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
