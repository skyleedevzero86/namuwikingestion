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

CREATE TABLE IF NOT EXISTS search_ui_config (
    key   VARCHAR(255) PRIMARY KEY,
    value TEXT NOT NULL
);
INSERT INTO search_ui_config (key, value) VALUES
    ('search2.sqlSectionLabel', 'Generated SQL'),
    ('search2.explanationSectionLabel', 'Query Explanation'),
    ('search2.emptySqlPlaceholder', '-- 검색어 입력 후 SEARCH'),
    ('search2.emptyExplanationPlaceholder', '[]'),
    ('search2.noSearchSqlPlaceholder', '-- Vector: NONE, BM25: off. Enable at least one.'),
    ('search2.errorSqlPlaceholder', '-- 오류 발생'),
    ('search2.errorExplanationPlaceholder', '[]')
ON CONFLICT (key) DO NOTHING;
