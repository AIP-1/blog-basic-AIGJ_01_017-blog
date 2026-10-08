package com.nhnacademy.blog.post.presentation.dto;

import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.error.FieldErrorDetail;
import com.nhnacademy.blog.post.application.PostCommand;
import com.nhnacademy.blog.post.domain.PostStatus;
import com.nhnacademy.blog.post.domain.Topic;
import com.nhnacademy.blog.post.domain.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 글 저장 본문 (contracts/rest-api.md 글 저장 본문). POST /api/posts, PUT /api/posts/{id}가 같이 쓴다.
 * 스텝 5에서는 발행(PUBLISHED)과 공개·비공개만 된다. 나머지 칸은 기능이 생기는 스텝에서 받고,
 * 그 전에 값을 보내면 조용히 버리지 않고 400으로 알린다(checkSupported).
 */
public record PostSaveRequest(
        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 200, message = "제목은 200자까지입니다.")
        String title,

        String contentHtml,

        Long categoryId,

        List<String> tagNames,

        Topic topic,

        @NotNull(message = "공개 범위를 골라 주세요.")
        Visibility visibility,

        @NotNull(message = "저장 상태를 골라 주세요.")
        PostStatus status,

        LocalDateTime scheduledAt,

        Long thumbnailImageId,

        Boolean commentAllowed) {

    /** 아직 없는 기능의 값이 오면 400. 각 칸이 언제 열리는지는 메시지 옆 주석. */
    public void checkSupported() {
        List<FieldErrorDetail> errors = new ArrayList<>();
        if (status != PostStatus.PUBLISHED) {
            // 임시저장(POST-08), 예약 발행(POST-13)은 백로그
            errors.add(new FieldErrorDetail("status", "지금은 바로 발행만 할 수 있습니다."));
        }
        if (visibility == Visibility.SUBSCRIBERS) {
            // 구독(SUB-01)이 생기기 전에는 구독자 공개를 막는다 (contracts 글 저장 본문, review C-7)
            errors.add(new FieldErrorDetail("visibility", "구독자 공개는 아직 고를 수 없습니다."));
        }
        if (tagNames != null && !tagNames.isEmpty()) {
            errors.add(new FieldErrorDetail("tagNames", "태그는 아직 달 수 없습니다.")); // 스텝 7 (TAG-01)
        }
        if (scheduledAt != null) {
            errors.add(new FieldErrorDetail("scheduledAt", "예약 발행은 아직 할 수 없습니다.")); // POST-13
        }
        if (thumbnailImageId != null) {
            errors.add(new FieldErrorDetail("thumbnailImageId", "대표 이미지는 아직 고를 수 없습니다.")); // POST-07
        }
        if (Boolean.FALSE.equals(commentAllowed)) {
            errors.add(new FieldErrorDetail("commentAllowed", "댓글 막기는 아직 할 수 없습니다.")); // CMT-07
        }
        if (!errors.isEmpty()) {
            throw BusinessException.fieldErrors(ErrorCode.VALIDATION_FAILED, errors);
        }
    }

    public PostCommand toCommand() {
        return new PostCommand(title, contentHtml == null ? "" : contentHtml, categoryId, topic, visibility);
    }

}
