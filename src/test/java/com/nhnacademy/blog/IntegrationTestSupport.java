package com.nhnacademy.blog;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * 실제 MySQL·Redis(Testcontainers)로 전체 애플리케이션을 띄우는 테스트의 부모.
 * 설정이 같아야 Spring이 컨텍스트와 컨테이너를 테스트끼리 재사용한다.
 * 올린 이미지는 개발용 uploads/가 아니라 임시 폴더에 쓴다.
 * MockMvc 요청마다 다른 접속 주소를 준다(TestWebConfiguration, IP별 시도 제한이 테스트끼리 쌓이지 않게).
 * 비슷한 글 추천의 임베딩은 Ollama 대신 가짜를 쓴다(TestRecommendConfiguration).
 */
@SpringBootTest(properties = "app.upload.dir=${java.io.tmpdir}/blog-test-uploads")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestWebConfiguration.class, TestRecommendConfiguration.class})
public abstract class IntegrationTestSupport {
}
