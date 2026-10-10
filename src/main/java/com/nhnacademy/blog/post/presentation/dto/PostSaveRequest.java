package com.nhnacademy.blog.post.presentation.dto;

import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.error.FieldErrorDetail;
import com.nhnacademy.blog.post.application.PostCommand;
import com.nhnacademy.blog.post.domain.PostStatus;
import com.nhnacademy.blog.post.domain.Topic;
import com.nhnacademy.blog.post.domain.Visibility;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 글 저장 본문 (contracts/rest-api.md 글 저장 본문). POST /api/posts, PUT /api/posts/{id}가 같이 쓴다.
 * 발행(PUBLISHED)·임시저장(DRAFT, 스텝 13)과 공개·비공개, 태그(스텝 7), 대표 이미지(스텝 13)가 된다.
 * 나머지 칸은 기능이 생기는 스텝에서 받고, 그 전에 값을 보내면 조용히 버리지 않고 400으로 알린다(checkSupported).
 * 제목은 발행할 때만 필수다. 임시저장은 제목이 비어도 된다(contracts 글 저장 본문 status 표).
 */
public record PostSaveRequest(
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

    /**
     * 칸 형식 검사(Bean Validation) 뒤에 부른다. 발행인데 제목이 비었거나, 아직 없는 기능의 값이 오면 400.
     * 각 칸이 언제 열리는지는 메시지 옆 주석.
     */
    public void checkSupported() {
        List<FieldErrorDetail> errors = new ArrayList<>();
        if (status == PostStatus.PUBLISHED && (title == null || title.isBlank())) {
            errors.add(new FieldErrorDetail("title", "제목을 입력해 주세요."));
        }
        if (status == PostStatus.SCHEDULED) {
            errors.add(new FieldErrorDetail("status", "예약 발행은 아직 할 수 없습니다.")); // POST-13
        }
        if (scheduledAt != null) {
            errors.add(new FieldErrorDetail("scheduledAt", "예약 발행은 아직 할 수 없습니다.")); // POST-13
        }
        if (Boolean.FALSE.equals(commentAllowed)) {
            errors.add(new FieldErrorDetail("commentAllowed", "댓글 막기는 아직 할 수 없습니다.")); // CMT-07
        }
        if (!errors.isEmpty()) {
            throw BusinessException.fieldErrors(ErrorCode.VALIDATION_FAILED, errors);
        }
    }

    public PostCommand toCommand() {
        return new PostCommand(title == null ? "" : title.trim(), contentHtml == null ? "" : contentHtml, categoryId,
                topic, visibility, tagNames, status, thumbnailImageId);
    }

}
