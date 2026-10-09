package com.nhnacademy.blog.post.domain;

/** 글 주제 (POST-11). 10개 고정이고, 주제 없음은 null이다. 주제는 카테고리와 별개다. */
public enum Topic {
    IT_DEV("IT·개발"),
    TRAVEL("여행"),
    FOOD("맛집·요리"),
    DAILY("일상"),
    REVIEW("리뷰"),
    HOBBY("취미"),
    FINANCE("경제·재테크"),
    HEALTH("건강·운동"),
    CULTURE("문화·연예"),
    EDUCATION("교육·학습");

    private final String displayName;

    Topic(String displayName) {
        this.displayName = displayName;
    }

    /** 화면에 보이는 이름. */
    public String getDisplayName() {
        return displayName;
    }
}
