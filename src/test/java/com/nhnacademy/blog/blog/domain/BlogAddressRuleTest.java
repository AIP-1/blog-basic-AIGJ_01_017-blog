package com.nhnacademy.blog.blog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BlogAddressRuleTest {

    @ParameterizedTest
    @ValueSource(strings = {"alpha", "my-blog", "a1b2", "abcdefghijklmnopqrstuvwxyz012345"})
    void usable(String address) {
        assertThat(BlogAddressRule.isUsable(address)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "-alpha", "alpha-", "Alpha", "al_pha", "알파블로그",
            "abcdefghijklmnopqrstuvwxyz0123456", ""})
    void invalidFormat(String address) {
        assertThat(BlogAddressRule.hasValidFormat(address)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"www", "admin", "login", "static", "uploads"})
    void reserved(String address) {
        assertThat(BlogAddressRule.isUsable(address)).isFalse();
    }

}
