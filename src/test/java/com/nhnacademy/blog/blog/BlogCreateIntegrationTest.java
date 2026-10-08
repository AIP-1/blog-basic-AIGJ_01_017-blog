package com.nhnacademy.blog.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.blog.domain.Blog;
import com.nhnacademy.blog.blog.domain.BlogRepository;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.support.TestBlogs;
import com.nhnacademy.blog.support.TestMembers;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 블로그 주소 확인과 개설 (T022, T027, BLOG-01, spec US1 수용 시나리오 5·6·7).
 */
class BlogCreateIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    TestBlogs testBlogs;

    @Autowired
    BlogRepository blogRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Member member;
    Cookie[] cookies;

    @BeforeEach
    void setUp() {
        member = testMembers.create();
        cookies = testMembers.loginCookies(member);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-abcd", "abcd-", "abc", "Alpha", "ab_cd", "한글주소", "a234567890123456789012345678901234"})
    void addressBreakingRulesIsInvalid(String address) throws Exception {
        check(address)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("INVALID"));
        create(address)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BLOG_ADDRESS_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("address"));
    }

    @ParameterizedTest
    // www, api 같은 3자 예약어는 길이 규칙(4~32자)에서 먼저 INVALID다
    @ValueSource(strings = {"admin", "login", "static", "manage"})
    void reservedAddressIsRejected(String address) throws Exception {
        check(address).andExpect(jsonPath("$.reason").value("RESERVED"));
        create(address)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BLOG_ADDRESS_INVALID"));
    }

    @Test
    void takenOrDeletedBlogAddressIsRejected() throws Exception {
        Blog used = testBlogs.create(testMembers.create());
        Blog deleted = testBlogs.create(testMembers.create());
        testBlogs.delete(deleted);

        for (Blog blog : List.of(used, deleted)) {
            check(blog.getAddress()).andExpect(jsonPath("$.reason").value("TAKEN"));
            create(blog.getAddress())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("BLOG_ADDRESS_TAKEN"));
        }
    }

    @Test
    void validFreeAddressIsAvailable() throws Exception {
        check(uniqueAddress())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reason").doesNotExist());
        check("a-" + uniqueAddress().substring(1, 6)).andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void firstBlogBecomesPrimary() throws Exception {
        String first = uniqueAddress();
        String second = uniqueAddress();

        create(first, "지원의 기록", "개발 공부")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address").value(first))
                .andExpect(jsonPath("$.name").value("지원의 기록"))
                .andExpect(jsonPath("$.description").value("개발 공부"))
                .andExpect(jsonPath("$.owner.id").value(member.getId()))
                .andExpect(jsonPath("$.owner.primaryBlogAddress").value(first))
                .andExpect(jsonPath("$.postCount").value(0))
                .andExpect(jsonPath("$.viewer.isOwner").value(true));
        create(second).andExpect(status().isCreated());

        assertThat(blogRepository.findByAddress(first).orElseThrow().isPrimary()).isTrue();
        assertThat(blogRepository.findByAddress(second).orElseThrow().isPrimary()).isFalse();
        mockMvc.perform(get("/api/me").cookie(cookies))
                .andExpect(jsonPath("$.primaryBlog.address").value(first));
    }

    @Test
    void sixthActiveBlogIsRejected() throws Exception {
        for (int i = 0; i < 5; i++) {
            create(uniqueAddress()).andExpect(status().isCreated());
        }

        create(uniqueAddress())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOG_LIMIT_EXCEEDED"));

        // 삭제한 블로그는 한도에 세지 않는다
        jdbcTemplate.update("UPDATE blog SET deleted_at = NOW() WHERE member_id = ? AND is_primary = 0 LIMIT 1",
                member.getId());
        create(uniqueAddress()).andExpect(status().isCreated());
    }

    @Test
    void concurrentCreatesKeepLimitAndSinglePrimary() throws Exception {
        List<Callable<Integer>> requests = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String address = uniqueAddress();
            requests.add(() -> create(address).andReturn().getResponse().getStatus());
        }

        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(7)) {
            for (Future<Integer> result : executor.invokeAll(requests)) {
                statuses.add(result.get());
            }
        }

        assertThat(statuses).filteredOn(code -> code == 201).hasSize(5);
        assertThat(statuses).filteredOn(code -> code == 409).hasSize(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM blog WHERE member_id = ? AND is_primary = 1", Integer.class, member.getId()))
                .isEqualTo(1);
    }

    @Test
    void nameIsRequiredAndLimitedTo50() throws Exception {
        create(uniqueAddress(), " ", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
        create(uniqueAddress(), "가".repeat(51), null).andExpect(status().isBadRequest());
    }

    @Test
    void loginIsRequired() throws Exception {
        mockMvc.perform(get("/api/blogs/address-availability").param("address", uniqueAddress()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/blogs")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"" + uniqueAddress() + "\",\"name\":\"이름\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions check(String address) throws Exception {
        return mockMvc.perform(get("/api/blogs/address-availability").param("address", address).cookie(cookies));
    }

    private ResultActions create(String address) throws Exception {
        return create(address, "블로그", null);
    }

    private ResultActions create(String address, String name, String description) throws Exception {
        String body = description == null
                ? "{\"address\":\"%s\",\"name\":\"%s\"}".formatted(address, name)
                : "{\"address\":\"%s\",\"name\":\"%s\",\"description\":\"%s\"}".formatted(address, name, description);
        return mockMvc.perform(post("/api/blogs")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .cookie(cookies));
    }

    private static String uniqueAddress() {
        return "b" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

}
