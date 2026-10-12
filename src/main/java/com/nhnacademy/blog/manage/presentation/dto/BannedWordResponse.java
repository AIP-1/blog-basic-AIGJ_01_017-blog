package com.nhnacademy.blog.manage.presentation.dto;

import com.nhnacademy.blog.manage.domain.BlogBannedWord;

/** 금칙어 한 줄 `{ id, word }`. */
public record BannedWordResponse(Long id, String word) {

    public static BannedWordResponse from(BlogBannedWord word) {
        return new BannedWordResponse(word.getId(), word.getWord());
    }

}
