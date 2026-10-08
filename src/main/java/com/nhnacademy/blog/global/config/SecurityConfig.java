package com.nhnacademy.blog.global.config;

import com.nhnacademy.blog.global.auth.AuthCookieManager;
import com.nhnacademy.blog.global.auth.AuthProperties;
import com.nhnacademy.blog.global.auth.CsrfHeaderFilter;
import com.nhnacademy.blog.global.auth.JwtAuthenticationFilter;
import com.nhnacademy.blog.global.auth.JwtTokenProvider;
import com.nhnacademy.blog.global.auth.SuspensionDetails;
import com.nhnacademy.blog.global.auth.TokenStore;
import com.nhnacademy.blog.global.error.ErrorCode;
import com.nhnacademy.blog.global.error.ErrorResponseWriter;
import com.nhnacademy.blog.member.domain.MemberRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * 보안 설정 (T007, R-03, ADMIN-01).
 * <ul>
 *   <li>로그인 상태는 쿠키의 JWT로만 판단한다. 서버 세션은 만들지 않는다.</li>
 *   <li>/api/admin/**은 서비스 관리자만. 나머지는 열어 두고 로그인이 필요한 API는 메서드에서 막는다.</li>
 *   <li>CSRF는 Spring 기본 토큰 대신 X-Requested-With 헤더로 막는다(CsrfHeaderFilter).</li>
 *   <li>401·403도 COM-02 오류 모양으로 준다.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthCookieManager cookieManager,
                                            JwtTokenProvider tokenProvider, TokenStore tokenStore,
                                            MemberRepository memberRepository,
                                            SuspensionDetails suspensionDetails,
                                            ErrorResponseWriter errorResponseWriter, AuthProperties authProperties,
                                            Clock clock) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(cookieManager, tokenProvider, tokenStore,
                memberRepository, suspensionDetails, errorResponseWriter, authProperties, clock);
        CsrfHeaderFilter csrfFilter = new CsrfHeaderFilter(errorResponseWriter);

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, e) ->
                                errorResponseWriter.write(response, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, e) ->
                                errorResponseWriter.write(response, ErrorCode.FORBIDDEN)))
                .addFilterBefore(csrfFilter, AnonymousAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, AnonymousAuthenticationFilter.class)
                .build();
    }

    /** 비밀번호 해시 (bcrypt). 관리자 초기 계정(V2)도 같은 방식이다. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
