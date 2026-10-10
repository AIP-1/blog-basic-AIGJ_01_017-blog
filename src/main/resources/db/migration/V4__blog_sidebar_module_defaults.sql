-- 사이드바 모듈 (T086, BLOG-05). 스텝 19부터 블로그를 만들 때 8개 행을 함께 만든다.
-- 그 전에 만든 블로그에 같은 기본 행을 채운다: 위에서부터 홈 바로가기, 카테고리, 태그, 최근 글, 최근 댓글은 보이고
-- 방문자 수, 인기 글, 구독은 숨김. 이미 행이 있는 블로그는 건드리지 않는다.
INSERT INTO blog_sidebar_module (blog_id, module_type, sort_order, is_visible)
SELECT b.id, m.module_type, m.sort_order, m.is_visible
FROM blog b
CROSS JOIN (
    SELECT 'PROFILE' AS module_type, 0 AS sort_order, 1 AS is_visible
    UNION ALL SELECT 'CATEGORY', 1, 1
    UNION ALL SELECT 'TAG', 2, 1
    UNION ALL SELECT 'RECENT_POST', 3, 1
    UNION ALL SELECT 'RECENT_COMMENT', 4, 1
    UNION ALL SELECT 'VISITOR', 5, 0
    UNION ALL SELECT 'POPULAR_POST', 6, 0
    UNION ALL SELECT 'SUBSCRIBE', 7, 0
) m
WHERE NOT EXISTS (SELECT 1 FROM blog_sidebar_module s WHERE s.blog_id = b.id);
