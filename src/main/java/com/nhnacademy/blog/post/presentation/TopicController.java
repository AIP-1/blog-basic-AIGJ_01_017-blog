package com.nhnacademy.blog.post.presentation;

import com.nhnacademy.blog.post.domain.Topic;
import com.nhnacademy.blog.post.presentation.dto.TopicResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 고정 주제 10개 (T056, POST-11, HOME-03). 글쓰기 화면의 주제 고르기와 홈 주제 탭이 같이 쓴다. 어느 주소에서나 부른다.
 */
@RestController
public class TopicController {

    private static final List<TopicResponse> TOPICS = Arrays.stream(Topic.values()).map(TopicResponse::from).toList();

    @GetMapping("/api/topics")
    public List<TopicResponse> topics() {
        return TOPICS;
    }

}
