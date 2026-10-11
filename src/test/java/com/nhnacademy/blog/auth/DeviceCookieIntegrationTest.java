package com.nhnacademy.blog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.support.TestMembers;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 전에 로그인한 브라우저는 잠그지 않기 (스텝 20: T055b, R-17, OWASP device cookie). */
class DeviceCookieIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Test
    void ownerWithDeviceCookieStillLogsInWhenOthersLockTheEmail() throws Exception {
        Member owner = testMembers.createWithPassword("right1234");
        Cookie device = login(owner.getEmail(), "right1234", null).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("device");
        assertThat(device).isNotNull();
        assertThat(device.isHttpOnly()).isTrue();

        // 다른 사람(기기 쿠키 없음)이 5번 틀리면 이메일이 막힌다
        for (int i = 0; i < 5; i++) {
            login(owner.getEmail(), "wrong1234", null).andExpect(status().isUnauthorized());
        }
        login(owner.getEmail(), "right1234", null).andExpect(status().isTooManyRequests());

        // 전에 로그인한 브라우저는 들어온다
        login(owner.getEmail(), "right1234", device).andExpect(status().isOk());

        // 남의 기기 쿠키나 지어낸 값은 소용없다
        Member other = testMembers.createWithPassword("right1234");
        Cookie otherDevice = login(other.getEmail(), "right1234", null).andReturn().getResponse().getCookie("device");
        login(owner.getEmail(), "right1234", otherDevice).andExpect(status().isTooManyRequests());
        login(owner.getEmail(), "right1234", new Cookie("device", owner.getId() + ".abc.forged"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void deviceItselfIsLockedAfterFiveFailures() throws Exception {
        Member owner = testMembers.createWithPassword("right1234");
        Cookie device = login(owner.getEmail(), "right1234", null).andReturn().getResponse().getCookie("device");
        for (int i = 0; i < 5; i++) {
            login(owner.getEmail(), "wrong1234", device).andExpect(status().isUnauthorized());
        }
        login(owner.getEmail(), "right1234", device).andExpect(status().isTooManyRequests());
        // 이메일 쪽은 세지 않았으므로 다른 브라우저는 아직 들어온다
        login(owner.getEmail(), "right1234", null).andExpect(status().isOk());
    }

    private ResultActions login(String email, String password, Cookie device) throws Exception {
        var request = post("/api/auth/login")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
        return mockMvc.perform(device == null ? request : request.cookie(device));
    }

}
