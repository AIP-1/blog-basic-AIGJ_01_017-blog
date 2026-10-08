package com.nhnacademy.blog.tag.domain;

import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 글에 단 태그 이름 정리 (TAG-01). 앞뒤 공백과 앞의 #을 떼고, 빈 이름은 버리고,
 * 대소문자만 다른 이름은 하나로 합친다(처음 쓴 모양을 남긴다). 정리한 뒤 10개를 넘으면 400 TOO_MANY_TAGS.
 */
public final class TagNames {

    public static final int MAX_PER_POST = 10;

    private TagNames() {
    }

    public static List<String> normalize(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String name : raw) {
            String trimmed = name == null ? "" : name.trim().replaceFirst("^#+", "").trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > Tag.MAX_NAME_LENGTH) {
                throw BusinessException.invalidField("tagNames", "태그는 " + Tag.MAX_NAME_LENGTH + "자까지입니다.");
            }
            unique.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        if (unique.size() > MAX_PER_POST) {
            throw new BusinessException(ErrorCode.TOO_MANY_TAGS);
        }
        return new ArrayList<>(unique.values());
    }

}
