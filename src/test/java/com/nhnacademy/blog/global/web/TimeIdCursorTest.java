package com.nhnacademy.blog.global.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nhnacademy.blog.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimeIdCursorTest {

    @Test
    void roundTrip() {
        TimeIdCursor cursor = new TimeIdCursor(LocalDateTime.of(2026, 10, 8, 13, 20, 0, 123_456_000), 1532);

        assertThat(TimeIdCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void nullMeansFromTheStart() {
        assertThat(TimeIdCursor.decode(null)).isNull();
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> TimeIdCursor.decode("not-a-cursor")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> TimeIdCursor.decode("%%%")).isInstanceOf(BusinessException.class);
    }

    @Test
    void cursorResponseHasNextOnlyWhenMoreRemain() {
        CursorResponse<Integer> more = CursorResponse.of(List.of(1, 2, 3), 2, String::valueOf);
        CursorResponse<Integer> last = CursorResponse.of(List.of(1, 2), 2, String::valueOf);

        assertThat(more.content()).containsExactly(1, 2);
        assertThat(more.nextCursor()).isEqualTo("2");
        assertThat(last.content()).containsExactly(1, 2);
        assertThat(last.nextCursor()).isNull();
    }

}
