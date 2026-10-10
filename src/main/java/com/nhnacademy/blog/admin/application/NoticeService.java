package com.nhnacademy.blog.admin.application;

import com.nhnacademy.blog.admin.domain.Notice;
import com.nhnacademy.blog.admin.domain.NoticeRepository;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.error.FieldErrorDetail;
import com.nhnacademy.blog.global.web.PageQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공지 (T111, ADMIN-06). 누구나 읽고, 관리자만 쓰고 고치고 지운다. */
@Service
public class NoticeService {

    public static final int PAGE_SIZE = 10;
    private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final NoticeRepository noticeRepository;

    public NoticeService(NoticeRepository noticeRepository) {
        this.noticeRepository = noticeRepository;
    }

    @Transactional(readOnly = true)
    public Page<Notice> list(PageQuery page) {
        return noticeRepository.findAll(page.toPageable(NEWEST));
    }

    @Transactional(readOnly = true)
    public Optional<Notice> latest() {
        return noticeRepository.findFirstByOrderByCreatedAtDescIdDesc();
    }

    @Transactional(readOnly = true)
    public Notice find(Long id) {
        return noticeRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional
    public Notice write(Long adminId, String title, String content) {
        Input input = Input.of(title, content);
        return noticeRepository.save(Notice.write(adminId, input.title(), input.content()));
    }

    @Transactional
    public void edit(Long id, String title, String content) {
        Notice notice = find(id);
        Input input = Input.of(title, content);
        notice.edit(input.title(), input.content());
    }

    @Transactional
    public void delete(Long id) {
        noticeRepository.delete(find(id));
    }

    /** 제목 1~200자, 내용 1~10,000자(앞뒤 공백 제거). 틀린 칸을 모두 알려 준다. */
    private record Input(String title, String content) {

        static Input of(String rawTitle, String rawContent) {
            String title = rawTitle == null ? "" : rawTitle.strip();
            String content = rawContent == null ? "" : rawContent.strip();
            List<FieldErrorDetail> errors = new ArrayList<>();
            if (title.isEmpty() || title.length() > Notice.TITLE_LENGTH) {
                errors.add(new FieldErrorDetail("title", "제목은 1~" + Notice.TITLE_LENGTH + "자입니다."));
            }
            if (content.isEmpty() || content.length() > Notice.CONTENT_LENGTH) {
                errors.add(new FieldErrorDetail("content", "내용은 1~10,000자입니다."));
            }
            if (!errors.isEmpty()) {
                throw BusinessException.fieldErrors(ErrorCode.VALIDATION_FAILED, errors);
            }
            return new Input(title, content);
        }

    }

}
