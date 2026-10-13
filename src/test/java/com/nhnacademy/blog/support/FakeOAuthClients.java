package com.nhnacademy.blog.support;

import com.nhnacademy.blog.auth.application.OAuthClient;
import com.nhnacademy.blog.auth.application.OAuthClients;
import com.nhnacademy.blog.auth.domain.SocialProvider;
import java.util.Arrays;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 테스트용 카카오·구글 (T099). 진짜 제공사 대신, 콜백에 온 code를 그대로 제공사 회원 번호로 돌려준다.
 * code가 "fail"로 시작하면 토큰 교환이 실패한 것으로 본다.
 */
@Component
@Primary
public class FakeOAuthClients extends OAuthClients {

    public FakeOAuthClients() {
        super(Arrays.stream(SocialProvider.values()).<OAuthClient>map(FakeClient::new).toList());
    }

    record FakeClient(SocialProvider provider) implements OAuthClient {

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public String authorizeUrl(String state, boolean reauth) {
            return "https://" + provider.path() + ".example/authorize?state=" + state + (reauth ? "&reauth" : "");
        }

        @Override
        public String fetchUserId(String code) {
            if (code.startsWith("fail")) {
                throw new IllegalStateException("토큰 교환 실패");
            }
            return code;
        }

    }

}
