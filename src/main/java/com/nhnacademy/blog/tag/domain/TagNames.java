package com.nhnacademy.blog.tag.domain;

import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 글에 단 태그 이름 정리 (TAG-01). 앞뒤 공백과 앞의 #을 떼고, 빈 이름은 버리고, 30자를 넘거나 /가 있으면 400,
 * 대소문자만 다른 이름은 하나로 합친다(처음 쓴 모양을 남긴다). 정리한 뒤 10개를 넘으면 400 TOO_MANY_TAGS.
 * "같은 이름"은 DB의 UNIQUE(blog_id, name)와 맞춘다. tag.name은 citext라 소문자로 바꿔 비교하므로 Java와 cafe는 같고,
 * 악센트는 가린다(Café와 cafe는 다르다, 스텝 23 R-02). 자바에서도 소문자로 바꿔 비교한다.
 */
public final class TagNames {

    public static final int MAX_PER_POST = 10;

    private TagNames() {
    }

    public static List<String> normalize(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        Map<String, String> unique = new TreeMap<>(nameComparator());
        List<String> ordered = new ArrayList<>();
        for (String name : raw) {
            String trimmed = name == null ? "" : name.trim().replaceFirst("^#+", "").trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > Tag.MAX_NAME_LENGTH) {
                throw BusinessException.invalidField("tagNames", "태그는 " + Tag.MAX_NAME_LENGTH + "자까지입니다.");
            }
            if (trimmed.contains("/")) {
                // 태그 목록 주소 /tag/{이름}에서 %2F가 되어 보안 방화벽이 거절한다 (spec TAG-01)
                throw BusinessException.invalidField("tagNames", "태그에는 /를 쓸 수 없습니다.");
            }
            if (unique.putIfAbsent(trimmed, trimmed) == null) {
                ordered.add(trimmed);
            }
        }
        if (unique.size() > MAX_PER_POST) {
            throw new BusinessException(ErrorCode.TOO_MANY_TAGS);
        }
        return ordered;
    }

    /**
     * 태그 하나의 이름 정리 (TAG-04 이름 바꾸기). 글에 달 때와 같은 규칙이다: 앞뒤 공백과 앞의 #을 떼고,
     * 비면 400, 30자를 넘거나 /가 있으면 400. 오류 칸 이름은 name.
     */
    public static String normalizeOne(String raw) {
        String trimmed = raw == null ? "" : raw.trim().replaceFirst("^#+", "").trim();
        if (trimmed.isEmpty()) {
            throw BusinessException.invalidField("name", "태그 이름을 입력해 주세요.");
        }
        if (trimmed.length() > Tag.MAX_NAME_LENGTH) {
            throw BusinessException.invalidField("name", "태그는 " + Tag.MAX_NAME_LENGTH + "자까지입니다.");
        }
        if (trimmed.contains("/")) {
            throw BusinessException.invalidField("name", "태그에는 /를 쓸 수 없습니다.");
        }
        return trimmed;
    }

    /** DB(citext)처럼 대소문자를 무시하고 이름을 비교한다. 같으면 원래 글자 순서로 가르지 않는다(같은 이름). */
    public static Comparator<String> nameComparator() {
        return Comparator.comparing(name -> name.toLowerCase(Locale.ROOT));
    }

}
