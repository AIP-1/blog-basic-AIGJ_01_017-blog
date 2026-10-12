package com.nhnacademy.blog.manage.application;

import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.global.auth.LoginMembers;
import com.nhnacademy.blog.global.host.DomainProperties;
import com.nhnacademy.blog.global.web.VisitorKeys;
import com.nhnacademy.blog.manage.domain.BlogStatsRepository;
import com.nhnacademy.blog.manage.domain.ReferrerType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * 블로그 방문 기록 (T082, MNG-03, R-14). 블로그 주소의 화면 요청(새로 열기·새로고침)마다 부른다.
 * 블로그·날짜·방문자마다 하루 한 번만 남고(UNIQUE), 블로그 주인 본인은 세지 않는다. 방문자는 조회 기록과 같은 키(VisitorKeys)다.
 * 기록에 실패해도 화면은 그대로 보여야 하므로 오류는 로그만 남긴다.
 */
@Component
public class VisitRecorder {

    private static final Logger log = LoggerFactory.getLogger(VisitRecorder.class);

    private final BlogStatsRepository blogStatsRepository;
    private final VisitorKeys visitorKeys;
    private final DomainProperties domainProperties;
    private final Clock clock;

    public VisitRecorder(BlogStatsRepository blogStatsRepository, VisitorKeys visitorKeys,
                         DomainProperties domainProperties, Clock clock) {
        this.blogStatsRepository = blogStatsRepository;
        this.visitorKeys = visitorKeys;
        this.domainProperties = domainProperties;
        this.clock = clock;
    }

    public void record(Blog blog, HttpServletRequest request, HttpServletResponse response) {
        if (blog.isOwnedBy(LoginMembers.currentId())) {
            return;
        }
        try {
            ReferrerType.Classified referrer = ReferrerType.classify(request.getHeader(HttpHeaders.REFERER),
                    domainProperties.platform());
            blogStatsRepository.recordVisit(blog.getId(), LocalDate.now(clock), visitorKeys.resolve(request, response),
                    referrer.type(), referrer.host());
        } catch (RuntimeException e) {
            log.warn("방문 기록 실패 blog={}", blog.getId(), e);
        }
    }

}
