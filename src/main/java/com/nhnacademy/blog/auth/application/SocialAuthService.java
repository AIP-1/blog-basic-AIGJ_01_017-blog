package com.nhnacademy.blog.auth.application;

import com.nhnacademy.blog.auth.domain.SocialAccount;
import com.nhnacademy.blog.auth.domain.SocialAccountRepository;
import com.nhnacademy.blog.auth.domain.SocialProvider;
import com.nhnacademy.blog.global.auth.SuspensionDetail;
import com.nhnacademy.blog.global.auth.SuspensionDetails;
import com.nhnacademy.blog.global.error.BusinessException;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.host.BlogAddressResolver;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인·가입·연동·재인증 (T099, T100, AUTH-01 소셜, OWN-03, AUTH-06, R-07). OAuth 2.0 인가 코드 흐름이다.
 * <ol>
 *   <li>start: 추측할 수 없는 state를 만들어 Redis에 10분 두고(무엇을 하려는지, 돌아갈 곳, 누가 시작했는지),
 *       같은 값을 이 브라우저의 쿠키에도 둔다. 그리고 제공사 로그인 화면 주소를 돌려준다.</li>
 *   <li>callback: 제공사가 돌려보낸 state를 Redis에서 꺼내 한 번 쓰고 지운다. code를 제공사 회원 번호로 바꿔 2분짜리 표(ticket)에 둔다.
 *       콜백 주소는 제공사 콘솔에 등록한 주소라 플랫폼 주소가 아닐 수 있다(개발에서 localhost로 등록한 경우).
 *       그래서 로그인 쿠키는 여기서 주지 않고, 시작한 플랫폼 주소의 complete로 표를 들려 보낸다.</li>
 *   <li>complete: 플랫폼 주소에서 표를 꺼내고, 이 브라우저의 state 쿠키가 표의 state와 같은지 본다.
 *       다른 사람이 자기 code가 든 콜백 주소를 내게 열게 해 나를 그 사람 계정으로 로그인시키는 공격(로그인 CSRF)을 막는다.
 *       그다음 mode에 따라 로그인·가입 안내·연동·재인증을 하고 갈 곳을 정한다.</li>
 * </ol>
 * 제공사 이메일은 받지도 저장하지도 않고, 이메일이 같다고 회원을 합치지 않는다(R-07).
 */
@Service
public class SocialAuthService {

    public static final Duration STATE_TTL = Duration.ofMinutes(10);
    static final Duration TICKET_TTL = Duration.ofMinutes(2);
    static final Duration SIGNUP_TTL = Duration.ofMinutes(10);

    private static final String STATE_PREFIX = "oauth:state:";
    private static final String TICKET_PREFIX = "oauth:ticket:";
    private static final String SIGNUP_PREFIX = "oauth:signup:";

    private static final Logger log = LoggerFactory.getLogger(SocialAuthService.class);

    private final OAuthClients clients;
    private final SocialAccountRepository socialAccountRepository;
    private final MemberRepository memberRepository;
    private final CredentialService credentialService;
    private final BlogAddressResolver hostResolver;
    private final SuspensionDetails suspensionDetails;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public SocialAuthService(OAuthClients clients, SocialAccountRepository socialAccountRepository,
                             MemberRepository memberRepository, CredentialService credentialService,
                             BlogAddressResolver hostResolver, SuspensionDetails suspensionDetails,
                             StringRedisTemplate redis, Clock clock) {
        this.clients = clients;
        this.socialAccountRepository = socialAccountRepository;
        this.memberRepository = memberRepository;
        this.credentialService = credentialService;
        this.hostResolver = hostResolver;
        this.suspensionDetails = suspensionDetails;
        this.redis = redis;
        this.clock = clock;
    }

    /** 제공사로 보낼 주소와, 이 브라우저의 쿠키에 둘 state. */
    public record Start(String state, String providerUrl) {
    }

    /**
     * 시작. origin은 지금 요청과 같은 프로토콜·포트의 플랫폼 주소(예: http://blog.test:5173)라 끝나면 그 주소로 돌아온다.
     * 앱 키가 없는 제공사면 빈 값이다. link·reauth는 로그인한 회원(memberId)만 부른다.
     */
    public Optional<Start> start(SocialProvider provider, OAuthMode mode, String redirect, Long memberId,
                                 String origin) {
        return clients.configured(provider).map(client -> {
            String state = randomToken();
            Map<String, String> data = new HashMap<>();
            data.put("provider", provider.name());
            data.put("mode", mode.name());
            data.put("origin", origin);
            if (redirect != null) {
                data.put("redirect", redirect);
            }
            if (memberId != null) {
                data.put("memberId", memberId.toString());
            }
            put(STATE_PREFIX + state, data, STATE_TTL);
            return new Start(state, client.authorizeUrl(state, mode == OAuthMode.REAUTH));
        });
    }

    /**
     * 제공사 콜백. 다음에 갈 절대 주소를 돌려준다. state가 없거나 이미 썼거나 다른 제공사 것이면 빈 값(부르는 쪽이 로그인 화면으로).
     * 사용자가 제공사 화면에서 취소했거나(error) 코드를 회원 번호로 바꾸지 못하면 시작한 화면으로 오류와 함께 돌려보낸다.
     */
    public Optional<String> callback(SocialProvider provider, String code, String state, String error) {
        Optional<Map<String, String>> found = take(STATE_PREFIX, state)
                .filter(data -> provider.name().equals(data.get("provider")));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> data = found.get();
        String origin = data.get("origin");
        OAuthMode mode = OAuthMode.valueOf(data.get("mode"));
        if (error != null || code == null || code.isBlank()) {
            return Optional.of(origin + failure(mode, "OAUTH_CANCELLED"));
        }
        String providerUserId;
        try {
            providerUserId = clients.configured(provider).orElseThrow().fetchUserId(code);
        } catch (RuntimeException e) {
            log.warn("{} 회원 번호를 받지 못했습니다: {}", provider, e.toString());
            return Optional.of(origin + failure(mode, "OAUTH_FAILED"));
        }
        String ticket = randomToken();
        Map<String, String> ticketData = new HashMap<>(data);
        ticketData.put("state", state);
        ticketData.put("providerUserId", providerUserId);
        put(TICKET_PREFIX + ticket, ticketData, TICKET_TTL);
        return Optional.of(origin + "/api/auth/oauth/complete?ticket=" + ticket);
    }

    /**
     * 끝내기. location은 플랫폼 주소 안의 경로이거나(예: /me?linked=kakao), 로그인이면 돌아갈 블로그 주소다.
     * login이 있으면 부르는 쪽이 그 회원의 로그인 쿠키를 준다.
     */
    public record Completion(String location, Member login) {

        static Completion to(String location) {
            return new Completion(location, null);
        }

    }

    /**
     * stateCookie는 이 브라우저의 state 쿠키, currentMemberId는 지금 로그인한 회원(없으면 null)이다.
     * 연동(link)은 저장이 실패하면 오류 주소로 돌려보내야 해서 트랜잭션을 이 메서드에 걸지 않는다(저장 하나가 각자 트랜잭션).
     */
    public Completion complete(String ticket, String stateCookie, Long currentMemberId) {
        Optional<Map<String, String>> found = take(TICKET_PREFIX, ticket)
                .filter(data -> data.get("state").equals(stateCookie));
        if (found.isEmpty()) {
            return Completion.to("/login?error=OAUTH_FAILED");
        }
        Map<String, String> data = found.get();
        SocialProvider provider = SocialProvider.valueOf(data.get("provider"));
        String providerUserId = data.get("providerUserId");
        Long startedBy = Optional.ofNullable(data.get("memberId")).map(Long::valueOf).orElse(null);
        return switch (OAuthMode.valueOf(data.get("mode"))) {
            case LOGIN -> login(provider, providerUserId, data.get("redirect"));
            case LINK -> startedBy == null || !startedBy.equals(currentMemberId)
                    ? Completion.to(failure(OAuthMode.LINK, "OAUTH_FAILED"))
                    : link(startedBy, provider, providerUserId);
            case REAUTH -> startedBy == null || !startedBy.equals(currentMemberId)
                    ? Completion.to(failure(OAuthMode.REAUTH, "OAUTH_FAILED"))
                    : reauth(startedBy, provider, providerUserId);
        };
    }

    /**
     * 처음 보는 소셜 계정의 닉네임 확인 뒤 가입 (AUTH-01). 회원(email·password 없음)과 소셜 연동을 한 트랜잭션으로 만든다.
     * 가입 토큰이 없거나 10분 지났으면 400 SOCIAL_SIGNUP_EXPIRED, 닉네임이 겹치면 409 NICKNAME_TAKEN,
     * 그 사이 같은 소셜 계정으로 먼저 가입했으면 409 SOCIAL_ALREADY_LINKED. 성공하면 토큰을 지운다(한 번만).
     */
    @Transactional
    public Member signup(String signupToken, String rawNickname) {
        Map<String, String> data = read(SIGNUP_PREFIX, signupToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIAL_SIGNUP_EXPIRED));
        SocialProvider provider = SocialProvider.valueOf(data.get("provider"));
        String providerUserId = data.get("providerUserId");
        String nickname = rawNickname.trim();
        if (memberRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_TAKEN);
        }
        if (socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId).isPresent()) {
            throw new BusinessException(ErrorCode.SOCIAL_ALREADY_LINKED);
        }
        Member member;
        try {
            member = memberRepository.saveAndFlush(Member.ofSocial(nickname));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.NICKNAME_TAKEN);
        }
        try {
            socialAccountRepository.saveAndFlush(SocialAccount.link(member.getId(), provider, providerUserId));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.SOCIAL_ALREADY_LINKED);
        }
        redis.delete(SIGNUP_PREFIX + signupToken);
        return member;
    }

    /**
     * 연동 해제 (OWN-03). 연결되어 있지 않으면 할 일이 없어 그대로 끝난다.
     * 해제하면 로그인 수단(비밀번호 또는 다른 소셜)이 하나도 남지 않으면 409 LAST_LOGIN_METHOD.
     * 회원 행을 잠그고 세므로, 두 제공사를 동시에 해제해도 둘 다 빠지는 일이 없다.
     */
    @Transactional
    public void unlink(Long memberId, SocialProvider provider) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Optional<SocialAccount> account = socialAccountRepository.findByMemberIdAndProvider(memberId, provider);
        if (account.isEmpty()) {
            return;
        }
        boolean otherMethodLeft = credentialService.hasPassword(member)
                || socialAccountRepository.countByMemberId(memberId) > 1;
        if (!otherMethodLeft) {
            throw new BusinessException(ErrorCode.LAST_LOGIN_METHOD);
        }
        socialAccountRepository.delete(account.get());
    }

    /** 연결된 소셜 계정이면 그 회원으로 로그인(정지면 로그인 화면에 정지 안내), 처음 보는 계정이면 닉네임 확인 화면으로. */
    private Completion login(SocialProvider provider, String providerUserId, String redirect) {
        Optional<SocialAccount> account = socialAccountRepository.findByProviderAndProviderUserId(provider,
                providerUserId);
        if (account.isEmpty()) {
            String token = randomToken();
            put(SIGNUP_PREFIX + token, Map.of("provider", provider.name(), "providerUserId", providerUserId),
                    SIGNUP_TTL);
            return Completion.to("/signup/social?token=" + token + "&provider=" + provider.path());
        }
        Optional<Member> member = memberRepository.findById(account.get().getMemberId())
                .filter(found -> !found.isWithdrawn());
        if (member.isEmpty()) {
            return Completion.to("/login?error=OAUTH_FAILED");
        }
        if (member.get().isSuspendedAt(LocalDateTime.now(clock))) {
            return Completion.to(suspendedNotice(suspensionDetails.of(member.get())));
        }
        return new Completion(safeRedirect(redirect), member.get());
    }

    /**
     * 정지 안내 주소. 로그인 화면이 이메일 로그인(403 MEMBER_SUSPENDED)과 같은 사유·기한을 그리게 붙인다.
     * 본인이 방금 로그인한 계정의 정보라 본인 브라우저에만 보인다. 기한이 없으면 영구 정지.
     */
    private static String suspendedNotice(SuspensionDetail detail) {
        StringBuilder location = new StringBuilder("/login?error=MEMBER_SUSPENDED");
        if (detail.reasonMessage() != null) {
            location.append("&reasonMessage=").append(URLEncoder.encode(detail.reasonMessage(), StandardCharsets.UTF_8));
        }
        if (detail.suspendedUntil() != null) {
            location.append("&until=").append(URLEncoder.encode(detail.suspendedUntil().toString(), StandardCharsets.UTF_8));
        }
        return location.toString();
    }

    /** 마이페이지 연동. 다른 회원에 연결된 계정, 같은 제공사가 이미 연결됨은 거절(R-07). 이미 내 것이면 그대로 성공. */
    private Completion link(Long memberId, SocialProvider provider, String providerUserId) {
        Optional<SocialAccount> owner = socialAccountRepository.findByProviderAndProviderUserId(provider,
                providerUserId);
        if (owner.isPresent()) {
            return Completion.to(owner.get().getMemberId().equals(memberId)
                    ? "/me?linked=" + provider.path()
                    : failure(OAuthMode.LINK, ErrorCode.SOCIAL_ALREADY_LINKED.name()));
        }
        if (socialAccountRepository.findByMemberIdAndProvider(memberId, provider).isPresent()) {
            return Completion.to(failure(OAuthMode.LINK, ErrorCode.PROVIDER_ALREADY_LINKED.name()));
        }
        try {
            socialAccountRepository.saveAndFlush(SocialAccount.link(memberId, provider, providerUserId));
        } catch (DataIntegrityViolationException e) {
            // 확인과 저장 사이에 같은 계정·같은 제공사가 먼저 연결됐다
            boolean takenByOther = socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
                    .filter(found -> !found.getMemberId().equals(memberId))
                    .isPresent();
            return Completion.to(failure(OAuthMode.LINK, takenByOther
                    ? ErrorCode.SOCIAL_ALREADY_LINKED.name() : ErrorCode.PROVIDER_ALREADY_LINKED.name()));
        }
        return Completion.to("/me?linked=" + provider.path());
    }

    /** 탈퇴 본인 확인. 이 회원에 연결된 소셜 계정으로 다시 로그인했으면 10분짜리 확인 표시를 남긴다. */
    private Completion reauth(Long memberId, SocialProvider provider, String providerUserId) {
        boolean mine = socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .filter(account -> account.getMemberId().equals(memberId))
                .isPresent();
        if (!mine) {
            return Completion.to(failure(OAuthMode.REAUTH, "REAUTH_FAILED"));
        }
        credentialService.markSocialReauthenticated(memberId);
        return Completion.to("/me/withdraw?reauth=ok");
    }

    /** 실패하면 시작한 화면으로: 로그인 화면, 마이페이지, 탈퇴 화면. */
    public static String failure(OAuthMode mode, String error) {
        return switch (mode) {
            case LOGIN -> "/login?error=" + error;
            case LINK -> "/me?error=" + error;
            case REAUTH -> "/me/withdraw?error=" + error;
        };
    }

    /** 로그인 뒤 돌아갈 곳. 우리 서비스 주소(플랫폼 호스트, 블로그는 그 아래 /@주소)나 경로만, 아니면 홈. */
    private String safeRedirect(String redirect) {
        if (redirect == null || redirect.isBlank()) {
            return "/";
        }
        if (redirect.startsWith("/") && !redirect.startsWith("//") && !redirect.startsWith("/\\")) {
            return redirect;
        }
        try {
            URI uri = URI.create(redirect);
            boolean web = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
            if (web && hostResolver.platform().equalsIgnoreCase(uri.getHost())) {
                return redirect;
            }
        } catch (IllegalArgumentException e) {
            // 주소 모양이 아니면 홈으로
        }
        return "/";
    }

    private void put(String key, Map<String, String> data, Duration ttl) {
        redis.opsForHash().putAll(key, data);
        redis.expire(key, ttl);
    }

    private Optional<Map<String, String>> read(String prefix, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Map<Object, Object> entries = redis.opsForHash().entries(prefix + token);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> data = new HashMap<>();
        entries.forEach((key, value) -> data.put(key.toString(), value.toString()));
        return Optional.of(data);
    }

    /** 읽고 지운다. 동시에 두 번 와도 지우기에 성공한 한 번만 쓴다. */
    private Optional<Map<String, String>> take(String prefix, String token) {
        return read(prefix, token).filter(data -> Boolean.TRUE.equals(redis.delete(prefix + token)));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
