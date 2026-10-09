-- 비슷한 글 추천(OWN-06) 전용 PostgreSQL. 글의 임베딩만 둔다 (R-02, T069b).
-- post_id는 MySQL post.id다. DB가 달라 외래 키를 걸 수 없으므로, 글을 지우면 애플리케이션이 이 행도 지운다.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE post_embedding (
    post_id    BIGINT PRIMARY KEY,
    embedding  vector(1024) NOT NULL,   -- Ollama bge-m3는 1024차원
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 코사인 거리(<=>)로 가까운 글을 찾을 때 쓰는 근사 최근접 이웃 인덱스(HNSW). 행이 적으면 PostgreSQL이 그냥 전부 읽기도 한다.
CREATE INDEX idx_post_embedding_embedding ON post_embedding USING hnsw (embedding vector_cosine_ops);
