package com.nhnacademy.blog.manage.presentation;

import com.nhnacademy.blog.blog.application.PrimaryBlogAddresses;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.BlogOwnerGuard;
import com.nhnacademy.blog.global.auth.LoginMember;
import com.nhnacademy.blog.global.host.CurrentBlog;
import com.nhnacademy.blog.global.web.DateTimes;
import com.nhnacademy.blog.global.web.PageQuery;
import com.nhnacademy.blog.global.web.PageResponse;
import com.nhnacademy.blog.image.application.ProfileImages;
import com.nhnacademy.blog.manage.application.BlogSpamService;
import com.nhnacademy.blog.manage.domain.BlogBlockedMember;
import com.nhnacademy.blog.manage.presentation.dto.BannedWordRequest;
import com.nhnacademy.blog.manage.presentation.dto.BannedWordResponse;
import com.nhnacademy.blog.manage.presentation.dto.BlockRequest;
import com.nhnacademy.blog.manage.presentation.dto.BlockedMemberResponse;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberRepository;
import com.nhnacademy.blog.member.presentation.dto.MemberSummaryResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 스팸·차단 관리 (T084, MNG-04). 블로그 주소에서 주인만. */
@RestController
public class ManageSpamController {

    private final BlogSpamService blogSpamService;
    private final BlogOwnerGuard blogOwnerGuard;
    private final MemberRepository memberRepository;
    private final PrimaryBlogAddresses primaryBlogAddresses;
    private final ProfileImages profileImages;

    public ManageSpamController(BlogSpamService blogSpamService, BlogOwnerGuard blogOwnerGuard,
                                MemberRepository memberRepository, PrimaryBlogAddresses primaryBlogAddresses,
                                ProfileImages profileImages) {
        this.blogSpamService = blogSpamService;
        this.blogOwnerGuard = blogOwnerGuard;
        this.memberRepository = memberRepository;
        this.primaryBlogAddresses = primaryBlogAddresses;
        this.profileImages = profileImages;
    }

    @GetMapping("/api/manage/blocked-members")
    public PageResponse<BlockedMemberResponse> blocked(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                                                       @RequestParam(required = false) Integer page) {
        blogOwnerGuard.requireOwner(blog, member);
        Page<BlogBlockedMember> blocked = blogSpamService.blocked(blog, PageQuery.of(page, null, BlogSpamService.PAGE_SIZE));
        List<Long> ids = blocked.getContent().stream().map(BlogBlockedMember::getBlockedMemberId).toList();
        Map<Long, Member> members = memberRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        Map<Long, String> addresses = primaryBlogAddresses.of(ids, member.id());
        Map<Long, String> photos = profileImages.thumbnailUrls(members.values().stream().map(Member::getProfileImageId).toList());
        return PageResponse.from(blocked, row -> {
            Member blockedMember = members.get(row.getBlockedMemberId());
            return new BlockedMemberResponse(MemberSummaryResponse.of(blockedMember,
                    addresses.get(blockedMember.getId()), photos.get(blockedMember.getProfileImageId())),
                    row.getMemo(), DateTimes.toOffset(row.getCreatedAt()));
        });
    }

    @PostMapping("/api/manage/blocked-members")
    @ResponseStatus(HttpStatus.CREATED)
    public void block(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                      @RequestBody BlockRequest request) {
        blogOwnerGuard.requireOwner(blog, member);
        blogSpamService.block(blog, request.memberId(), request.memo());
    }

    @DeleteMapping("/api/manage/blocked-members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                        @PathVariable Long memberId) {
        blogOwnerGuard.requireOwner(blog, member);
        blogSpamService.unblock(blog, memberId);
    }

    @GetMapping("/api/manage/banned-words")
    public List<BannedWordResponse> bannedWords(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member) {
        blogOwnerGuard.requireOwner(blog, member);
        return blogSpamService.bannedWords(blog).stream().map(BannedWordResponse::from).toList();
    }

    @PostMapping("/api/manage/banned-words")
    @ResponseStatus(HttpStatus.CREATED)
    public BannedWordResponse addBannedWord(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                                            @RequestBody BannedWordRequest request) {
        blogOwnerGuard.requireOwner(blog, member);
        return BannedWordResponse.from(blogSpamService.addBannedWord(blog, request.word()));
    }

    @DeleteMapping("/api/manage/banned-words/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBannedWord(@CurrentBlog Blog blog, @AuthenticationPrincipal LoginMember member,
                                 @PathVariable Long id) {
        blogOwnerGuard.requireOwner(blog, member);
        blogSpamService.deleteBannedWord(blog, id);
    }

}
