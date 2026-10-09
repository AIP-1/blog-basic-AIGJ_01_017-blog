package com.nhnacademy.blog.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nhnacademy.blog.IntegrationTestSupport;
import com.nhnacademy.blog.member.domain.Member;
import com.nhnacademy.blog.member.domain.MemberRepository;
import com.nhnacademy.blog.support.TestMembers;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 회원정보 수정 (T055, AUTH-05): 닉네임, 프로필 사진, 비밀번호.
 */
class MeUpdateIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestMembers testMembers;

    @Autowired
    MemberRepository memberRepository;

    @Test
    void anonymousGets401BeforeInputErrors() throws Exception {
        send(patch("/api/me"), null, "{\"nickname\":\"\"}").andExpect(status().isUnauthorized());
        send(put("/api/me/password"), null, "{}").andExpect(status().isUnauthorized());
    }

    @Test
    void nicknameChangesUnlessAnotherMemberUsesIt() throws Exception {
        Member member = testMembers.create();
        Member other = testMembers.create();
        Cookie[] cookies = testMembers.loginCookies(member);
        String nickname = "새" + UUID.randomUUID().toString().substring(0, 8);

        send(patch("/api/me"), cookies, "{\"nickname\":\"  " + nickname + "  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(nickname))
                .andExpect(jsonPath("$.email").value(member.getEmail()));
        send(get("/api/me"), cookies, null).andExpect(jsonPath("$.nickname").value(nickname));

        // 대소문자만 다른 다른 회원의 닉네임도 같은 닉네임이다(DB 정렬 규칙)
        send(patch("/api/me"), cookies, "{\"nickname\":\"" + other.getNickname().toUpperCase() + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_TAKEN"));
        // 내 닉네임의 대소문자만 바꾸는 것은 된다
        send(patch("/api/me"), cookies, "{\"nickname\":\"" + nickname.toUpperCase() + "\"}")
                .andExpect(status().isOk());

        send(patch("/api/me"), cookies, "{\"nickname\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("nickname"));
        send(patch("/api/me"), cookies, "{\"nickname\":\"" + "가".repeat(21) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("nickname"));
    }

    @Test
    void profileImageMustBeMyOwnUpload() throws Exception {
        Cookie[] mine = testMembers.loginCookies(testMembers.create());
        Cookie[] others = testMembers.loginCookies(testMembers.create());
        long myImage = upload(mine);
        long otherImage = upload(others);

        send(patch("/api/me"), mine, "{\"profileImageId\":" + otherImage + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("profileImageId"));
        send(patch("/api/me"), mine, "{\"profileImageId\":" + myImage + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value(org.hamcrest.Matchers.startsWith("/uploads/t_")));
        // 닉네임만 보내면 사진은 그대로다
        send(patch("/api/me"), mine, "{}").andExpect(jsonPath("$.profileImageUrl").isString());
    }

    @Test
    void passwordChangeNeedsCurrentPasswordAndFollowsSignupRule() throws Exception {
        Member member = testMembers.createWithPassword("oldpass123");
        Cookie[] cookies = testMembers.loginCookies(member);

        send(put("/api/me/password"), cookies, "{\"currentPassword\":\"wrong1234\",\"newPassword\":\"newpass123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));
        send(put("/api/me/password"), cookies, "{\"currentPassword\":\"oldpass123\",\"newPassword\":\"short1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
        send(put("/api/me/password"), cookies, "{\"currentPassword\":\"oldpass123\",\"newPassword\":\"newpass123\"}")
                .andExpect(status().isNoContent());

        login(member.getEmail(), "oldpass123").andExpect(status().isUnauthorized());
        login(member.getEmail(), "newpass123").andExpect(status().isOk());
    }

    @Test
    void socialMemberHasNoPasswordToChange() throws Exception {
        Member social = memberRepository.save(Member.ofSocial("s-" + UUID.randomUUID().toString().substring(0, 8)));
        send(put("/api/me/password"), testMembers.loginCookies(social),
                "{\"currentPassword\":\"anything1\",\"newPassword\":\"newpass123\"}")
                .andExpect(status().isForbidden());
    }

    private long upload(Cookie[] cookies) throws Exception {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        String body = mockMvc.perform(multipart("/api/images")
                        .file(new MockMultipartFile("file", "me.png", "image/png", out.toByteArray()))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(cookies))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Cookie[] cookies, String body)
            throws Exception {
        request.header("X-Requested-With", "XMLHttpRequest");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (cookies != null) {
            request.cookie(cookies);
        }
        return mockMvc.perform(request);
    }

}
