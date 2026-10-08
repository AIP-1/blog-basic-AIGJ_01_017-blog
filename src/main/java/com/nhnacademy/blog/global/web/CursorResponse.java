package com.nhnacademy.blog.global.web;

import java.util.List;
import java.util.function.Function;

/**
 * 더보기(커서) 목록 응답. 끝이면 nextCursor가 null이다.
 */
public record CursorResponse<T>(List<T> content, String nextCursor) {

    /**
     * 한 개 더 읽은 결과(size + 1개)로 응답을 만든다. 넘친 것이 있으면 마지막 항목으로 다음 커서를 만든다.
     */
    public static <T> CursorResponse<T> of(List<T> fetched, int size, Function<T, String> cursorOf) {
        if (fetched.size() <= size) {
            return new CursorResponse<>(List.copyOf(fetched), null);
        }
        List<T> content = List.copyOf(fetched.subList(0, size));
        return new CursorResponse<>(content, cursorOf.apply(content.getLast()));
    }

}
