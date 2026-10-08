package com.nhnacademy.blog.tag.domain;

import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 글에 단 태그 이름 정리 (TAG-01). 앞뒤 공백과 앞의 #을 떼고, 빈 이름은 버리고,
 * 대소문자·악센트만 다른 이름은 하나로 합친다(처음 쓴 모양을 남긴다). 정리한 뒤 10개를 넘으면 400 TOO_MANY_TAGS.
 * "같은 이름"은 DB의 UNIQUE(blog_id, name)와 맞춘다. 정렬 규칙 utf8mb4_0900_ai_ci는 대소문자(ci)와 악센트(ai)를
 * 무시하므로 Café와 cafe가 같다. 자바에서는 같은 유니코드 정렬 규칙(UCA)의 1차 비교(PRIMARY)로 흉내 낸다.
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
            if (unique.putIfAbsent(trimmed, trimmed) == null) {
                ordered.add(trimmed);
            }
        }
        if (unique.size() > MAX_PER_POST) {
            throw new BusinessException(ErrorCode.TOO_MANY_TAGS);
        }
        return ordered;
    }

    /** DB처럼 대소문자·악센트를 무시하고 이름을 비교한다. Collator는 스레드 안전하지 않아 쓸 때마다 만든다. */
    public static Collator nameComparator() {
        Collator collator = Collator.getInstance(Locale.ROOT);
        collator.setStrength(Collator.PRIMARY);
        return collator;
    }

}
