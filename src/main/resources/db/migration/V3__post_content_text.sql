-- 블로그 안 검색(SRCH-01)용 본문 글자 칸 (research R-16, Crowfoot ERD 버전 79, 2026-10-09 지원 결정).
-- 본문 HTML의 태그 이름(strong, href 등)으로 검색에 걸리지 않게, 태그를 뺀 글자를 따로 둔다.
-- 앞으로는 저장할 때 서버가 jsoup으로 채운다. 이미 있는 글은 여기서 정규식으로 태그만 지워 채운다.

ALTER TABLE post ADD COLUMN content_text MEDIUMTEXT NULL COMMENT '본문 글자-----본문에서 HTML 태그를 뺀 글자. 블로그 안 검색(SRCH-01)에 쓴다. 저장할 때 서버가 jsoup으로 만든다';

UPDATE post SET content_text = TRIM(REGEXP_REPLACE(content_html, '<[^>]+>', ' '));

ALTER TABLE post MODIFY COLUMN content_text MEDIUMTEXT NOT NULL COMMENT '본문 글자-----본문에서 HTML 태그를 뺀 글자. 블로그 안 검색(SRCH-01)에 쓴다. 저장할 때 서버가 jsoup으로 만든다';
