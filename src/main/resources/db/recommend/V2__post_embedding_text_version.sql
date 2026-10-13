-- 임베딩에 넣은 글자의 방식 번호 (스텝 22, T120, 2026-10-13 지원 결정).
-- 1 = 제목 + 본문 글자, 2 = 제목 + 태그 + 본문 글자. 이미 있는 행은 1이고, 서버가 뜰 때 지금 방식(PostEmbeddingService.TEXT_VERSION)이
-- 아닌 행을 다시 만든다.
ALTER TABLE post_embedding ADD COLUMN text_version SMALLINT NOT NULL DEFAULT 1;
