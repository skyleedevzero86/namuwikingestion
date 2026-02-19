CREATE EXTENSION IF NOT EXISTS textsearch_ko;

ALTER TABLE namuwiki_doc ADD COLUMN IF NOT EXISTS tsv_content tsvector;

CREATE INDEX IF NOT EXISTS idx_namuwiki_doc_tsv ON namuwiki_doc USING GIN (tsv_content);

CREATE OR REPLACE FUNCTION namuwiki_doc_tsv_trigger() RETURNS trigger AS $$
BEGIN
  new.tsv_content :=
    setweight(to_tsvector('korean', coalesce(new.title, '')), 'A') ||
    setweight(to_tsvector('korean', coalesce(new.content, '')), 'D');
  RETURN new;
END
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS namuwiki_doc_tsv_update ON namuwiki_doc;
CREATE TRIGGER namuwiki_doc_tsv_update
  BEFORE INSERT OR UPDATE OF title, content ON namuwiki_doc
  FOR EACH ROW EXECUTE FUNCTION namuwiki_doc_tsv_trigger();

UPDATE namuwiki_doc
SET tsv_content =
  setweight(to_tsvector('korean', coalesce(title, '')), 'A') ||
  setweight(to_tsvector('korean', coalesce(content, '')), 'D')
WHERE tsv_content IS NULL AND (title IS NOT NULL OR content IS NOT NULL);
