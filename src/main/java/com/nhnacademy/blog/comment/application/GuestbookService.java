package com.nhnacademy.blog.comment.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.comment.domain.Guestbook;
import com.nhnacademy.blog.comment.domain.GuestbookRepository;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.member.domain.MemberRepository;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방명록 (T066, CMT-04). 블로그 단위의 글이고 규칙은 댓글과 같다: 1~1,000자, 비밀글은 블로그 주인과 작성자만,
 * 답글은 한 단계, 답글이 남은 글을 지우면 자리만 남는다. 보는 사람 기준의 모양은 댓글과 같은 CommentViews가 정한다.
 * 블로그를 볼 수 없으면 @CurrentBlog가 먼저 404로 막는다. 차단·금칙어(MNG-04)는 백로그다.
 */
@Service
public class GuestbookService {

    public static final int PAGE_SIZE = 20;

    /** 최신순. 같은 시각이면 나중에 쓴 것(id가 큰 것)이 위. */
    private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Sort WRITTEN_ORDER = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

    private final GuestbookRepository guestbookRepository;
    private final MemberRepository memberRepository;
    private final CommentViews commentViews;
    private final Clock clock;

    public GuestbookService(GuestbookRepository guestbookRepository, MemberRepository memberRepository,
                            CommentViews commentViews, Clock clock) {
        this.guestbookRepository = guestbookRepository;
        this.memberRepository = memberRepository;
        this.commentViews = commentViews;
        this.clock = clock;
    }

    /**
     * 최상위 글 기준 20개씩 최신순 페이지, 답글은 부모 안에 작성순으로 모두 붙인다(spec US7 시나리오 3).
     * 지운 글은 빼되, 지우지 않은 답글이 있는 글은 자리로 남긴다. 페이지 수도 이 기준으로 센다.
     */
    @Transactional(readOnly = true)
    public Page<CommentView> list(Blog blog, Long viewerId, PageQuery page) {
        Specification<Guestbook> condition = (root, query, cb) -> {
            Subquery<Long> liveReply = query.subquery(Long.class);
            Root<Guestbook> reply = liveReply.from(Guestbook.class);
            liveReply.select(reply.get("id")).where(
                    cb.equal(reply.get("parent"), root),
                    cb.isNull(reply.get("deletedAt")));
            return cb.and(
                    cb.equal(root.get("blog").get("id"), blog.getId()),
                    cb.isNull(root.get("parent")),
                    cb.or(cb.isNull(root.get("deletedAt")), cb.exists(liveReply)));
        };
        Page<Guestbook> parents = guestbookRepository.findBy(condition,
                query -> query.project("member").page(page.toPageable(NEWEST)));
        List<Guestbook> replies = parents.isEmpty() ? List.of() : guestbookRepository.findBy(
                (root, query, cb) -> cb.and(
                        root.get("parent").in(parents.getContent()),
                        cb.isNull(root.get("deletedAt"))),
                query -> query.sortBy(WRITTEN_ORDER).project("member").all());
        List<CommentView> views = commentViews.threads(parents.getContent(), replies, blog, viewerId);
        return new PageImpl<>(views, parents.getPageable(), parents.getTotalElements());
    }

    /** 쓰기 전 확인: 비회원 401. 입력 검증(400)은 이 다음에 컨트롤러가 한다. */
    public void requireMember(LoginMember member) {
        if (member == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    @Transactional
    public CommentView write(Blog blog, LoginMember member, String content, Long parentId, boolean secret) {
        requireMember(member);
        var author = memberRepository.getReferenceById(member.id());
        Guestbook saved = guestbookRepository.save(parentId == null
                ? Guestbook.write(blog, author, content.trim(), secret)
                : Guestbook.reply(parentOf(blog, parentId), author, content.trim(), secret));
        guestbookRepository.flush();
        Guestbook written = guestbookRepository.findBy(
                (root, query, cb) -> cb.equal(root.get("id"), saved.getId()),
                query -> query.project("member").first()).orElseThrow();
        return commentViews.one(written, blog, member.id());
    }

    /** 고치기 전 확인: 없음 404 → 비회원 401 → 본인이 아님 403. */
    @Transactional(readOnly = true)
    public void checkEditable(Blog blog, Long id, LoginMember member) {
        editable(blog, id, member);
    }

    @Transactional
    public CommentView edit(Blog blog, Long id, LoginMember member, String content) {
        Guestbook guestbook = editable(blog, id, member);
        guestbook.edit(content.trim());
        guestbookRepository.flush();
        return commentViews.one(guestbook, blog, member.id());
    }

    /** 작성자 본인과 블로그 주인만. 답글이 남아 있으면 목록에 자리만 남는다. */
    @Transactional
    public void delete(Blog blog, Long id, LoginMember member) {
        Guestbook guestbook = visible(blog, id, member);
        if (!guestbook.isWrittenBy(member.id()) && !blog.isOwnedBy(member.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        guestbook.delete(LocalDateTime.now(clock));
    }

    /** 답글을 달 글. 이 블로그의, 지우지 않은 최상위 글이어야 한다. 답글의 답글은 400(한 단계). */
    private Guestbook parentOf(Blog blog, Long parentId) {
        Guestbook parent = guestbookRepository.findById(parentId)
                .filter(found -> found.getBlog().getId().equals(blog.getId()) && !found.isDeleted())
                .orElseThrow(() -> BusinessException.invalidField("parentId", "답글을 달 글을 찾을 수 없습니다."));
        if (parent.getParent() != null) {
            throw BusinessException.invalidField("parentId", "답글에는 답글을 달 수 없습니다.");
        }
        return parent;
    }

    private Guestbook editable(Blog blog, Long id, LoginMember member) {
        Guestbook guestbook = visible(blog, id, member);
        if (!guestbook.isWrittenBy(member.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return guestbook;
    }

    /** 이 블로그의 지우지 않은 글. 그다음 로그인했는지 본다(404 → 401 순서, 댓글과 같음). */
    private Guestbook visible(Blog blog, Long id, LoginMember member) {
        Guestbook guestbook = guestbookRepository.findBy(
                        (root, query, cb) -> cb.equal(root.get("id"), id),
                        query -> query.project("member").first())
                .filter(found -> !found.isDeleted() && found.getBlog().getId().equals(blog.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        requireMember(member);
        return guestbook;
    }

}
