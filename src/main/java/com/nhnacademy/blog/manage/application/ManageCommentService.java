package com.nhnacademy.blog.manage.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.comment.application.CommentView;
import com.nhnacademy.blog.comment.application.CommentViews;
import com.nhnacademy.blog.comment.domain.Comment;
import com.nhnacademy.blog.comment.domain.CommentRepository;
import com.nhnacademy.blog.comment.domain.Guestbook;
import com.nhnacademy.blog.comment.domain.GuestbookRepository;
import com.nhnacademy.blog.global.web.PageQuery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 받은 댓글·방명록 관리 (T068, MNG-02). 주인 검사는 컨트롤러가 먼저 했다.
 * "받은" 것이라 주인 자신이 쓴 댓글·답글은 빼고, 지운 것과 지운 글의 댓글도 뺀다. 답글도 한 줄씩 나온다(parentId로 구분).
 * 보는 사람(주인) 기준의 모양은 글 상세와 같은 CommentViews가 정한다. 주인이라 비밀글 내용도 보인다.
 */
@Service
public class ManageCommentService {

    public static final int PAGE_SIZE = 20;

    private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final CommentRepository commentRepository;
    private final GuestbookRepository guestbookRepository;
    private final CommentViews commentViews;

    public ManageCommentService(CommentRepository commentRepository, GuestbookRepository guestbookRepository,
                                CommentViews commentViews) {
        this.commentRepository = commentRepository;
        this.guestbookRepository = guestbookRepository;
        this.commentViews = commentViews;
    }

    /** 이 블로그 글에 달린 남의 댓글, 최신순 20개씩. */
    @Transactional(readOnly = true)
    public Page<ReceivedComment> comments(Blog blog, PageQuery page) {
        Long ownerId = blog.getMember().getId();
        Specification<Comment> condition = (root, query, cb) -> cb.and(
                cb.equal(root.get("post").get("blog").get("id"), blog.getId()),
                cb.isNull(root.get("post").get("deletedAt")),
                cb.isNull(root.get("deletedAt")),
                cb.notEqual(root.get("member").get("id"), ownerId));
        Page<Comment> found = commentRepository.findBy(condition,
                query -> query.project("member", "post").page(page.toPageable(NEWEST)));
        List<CommentView> views = commentViews.flat(found.getContent(), blog, ownerId);
        List<ReceivedComment> rows = new ArrayList<>();
        for (int i = 0; i < views.size(); i++) {
            Comment comment = found.getContent().get(i);
            rows.add(new ReceivedComment(views.get(i), comment.getPost().getId(), comment.getPost().getTitle()));
        }
        return new PageImpl<>(rows, found.getPageable(), found.getTotalElements());
    }

    /** 이 블로그 방명록의 남의 글, 최신순 20개씩. */
    @Transactional(readOnly = true)
    public Page<ReceivedComment> guestbook(Blog blog, PageQuery page) {
        Long ownerId = blog.getMember().getId();
        Specification<Guestbook> condition = (root, query, cb) -> cb.and(
                cb.equal(root.get("blog").get("id"), blog.getId()),
                cb.isNull(root.get("deletedAt")),
                cb.notEqual(root.get("member").get("id"), ownerId));
        Page<Guestbook> found = guestbookRepository.findBy(condition,
                query -> query.project("member").page(page.toPageable(NEWEST)));
        List<ReceivedComment> rows = commentViews.flat(found.getContent(), blog, ownerId).stream()
                .map(view -> new ReceivedComment(view, null, null))
                .toList();
        return new PageImpl<>(rows, found.getPageable(), found.getTotalElements());
    }

}
