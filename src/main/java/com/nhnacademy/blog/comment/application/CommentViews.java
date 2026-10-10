package com.nhnacademy.blog.comment.application;

import com.nhnacademy.blog.admin.domain.ModerationAction;
import com.nhnacademy.blog.admin.domain.ModerationLogRepository;
import com.nhnacademy.blog.admin.domain.ModerationTargetType;
import com.nhnacademy.blog.blog.application.PrimaryBlogAddresses;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.comment.domain.CommentEntry;
import com.nhnacademy.blog.image.application.ProfileImages;
import com.nhnacademy.blog.member.domain.Member;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * 댓글·방명록을 보는 사람 기준의 CommentView로 바꾼다. 댓글(CommentService), 방명록(GuestbookService),
 * 관리 화면의 받은 댓글 목록(MNG-02)이 같은 규칙을 쓰도록 판단을 여기 한 곳에 둔다.
 * <ul>
 *   <li>지웠지만 답글이 남은 글: 누구에게나 내용·작성자 없이 자리만(DELETED)</li>
 *   <li>관리자가 숨긴 댓글: 작성자 본인에게는 내용과 사유, 다른 사람에게는 자리만(BLINDED, ADMIN-03)</li>
 *   <li>비밀글: 블로그 주인과 작성자만 내용을 본다(SECRET, CMT-06·CMT-04)</li>
 * </ul>
 * 작성자의 대표 블로그 주소와 프로필 사진은 한 번에 읽는다(N+1 방지). 호출하는 쪽의 트랜잭션 안에서 부른다.
 */
@Component
public class CommentViews {

    private final PrimaryBlogAddresses primaryBlogAddresses;
    private final ProfileImages profileImages;
    private final ModerationLogRepository moderationLogRepository;

    public CommentViews(PrimaryBlogAddresses primaryBlogAddresses, ProfileImages profileImages,
                        ModerationLogRepository moderationLogRepository) {
        this.primaryBlogAddresses = primaryBlogAddresses;
        this.profileImages = profileImages;
        this.moderationLogRepository = moderationLogRepository;
    }

    /** 최상위 글과 그 답글들을 묶는다. 답글은 replies 안에 받은 순서대로 붙는다. */
    public List<CommentView> threads(List<? extends CommentEntry> parents, List<? extends CommentEntry> replies,
                                     Blog blog, Long viewerId) {
        Authors authors = authors(Stream.concat(parents.stream(), replies.stream()).toList(), viewerId);
        Map<Long, List<CommentView>> repliesByParent = replies.stream().collect(Collectors.groupingBy(
                CommentEntry::getParentId, LinkedHashMap::new,
                Collectors.mapping(reply -> view(reply, blog, viewerId, authors), Collectors.toList())));
        return parents.stream()
                .map(parent -> view(parent, blog, viewerId, authors)
                        .withReplies(repliesByParent.getOrDefault(parent.getId(), List.of())))
                .toList();
    }

    /** 답글을 붙이지 않은 낱개 목록(관리 화면의 받은 댓글, 방금 쓰거나 고친 글). */
    public List<CommentView> flat(List<? extends CommentEntry> entries, Blog blog, Long viewerId) {
        Authors authors = authors(entries, viewerId);
        return entries.stream().map(entry -> view(entry, blog, viewerId, authors)).toList();
    }

    public CommentView one(CommentEntry entry, Blog blog, Long viewerId) {
        return flat(List.of(entry), blog, viewerId).getFirst();
    }

    private CommentView view(CommentEntry entry, Blog blog, Long viewerId, Authors authors) {
        boolean author = entry.isWrittenBy(viewerId);
        boolean blogOwner = blog.isOwnedBy(viewerId);
        if (entry.isDeleted()) {
            // 답글이 남아 자리만 있는 부모. 누구에게나 내용·작성자 없이, 다시 지우거나 고칠 것도 없다
            return new CommentView(entry, CommentView.State.DELETED, null, null, false, false, null, List.of());
        }
        CommentView.State state;
        Map<String, String> blind = null;
        if (entry.isBlinded()) {
            state = author ? CommentView.State.NORMAL : CommentView.State.BLINDED;
            blind = author ? blindReason(entry) : null;
        } else if (entry.isSecret() && !author && !blogOwner) {
            state = CommentView.State.SECRET;
        } else {
            state = CommentView.State.NORMAL;
        }
        Member member = entry.getMember();
        return new CommentView(entry, state, authors.addresses().get(member.getId()),
                authors.photos().get(member.getProfileImageId()), author && !entry.isBlinded(), author || blogOwner,
                blind, List.of());
    }

    /** 숨긴 댓글의 최신 BLIND 사유(관리 이력). 작성자 본인이 볼 때만 부른다. 방명록에는 숨김이 없다. */
    private Map<String, String> blindReason(CommentEntry entry) {
        return moderationLogRepository
                .findFirstByTargetTypeAndTargetIdAndActionOrderByCreatedAtDescIdDesc(
                        ModerationTargetType.COMMENT, entry.getId(), ModerationAction.BLIND)
                .map(log -> Map.of("reason", log.getReason().name(), "reasonMessage", log.getReason().getMessage()))
                .orElse(Map.of());
    }

    private Authors authors(List<? extends CommentEntry> entries, Long viewerId) {
        List<Member> members = entries.stream().map(CommentEntry::getMember).distinct().toList();
        return new Authors(primaryBlogAddresses.of(members.stream().map(Member::getId).toList(), viewerId),
                profileImages.thumbnailUrls(members.stream().map(Member::getProfileImageId).toList()));
    }

    private record Authors(Map<Long, String> addresses, Map<Long, String> photos) {
    }

}
