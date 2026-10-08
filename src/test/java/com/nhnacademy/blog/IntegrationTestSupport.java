package com.nhnacademy.blog;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * 실제 MySQL·Redis(Testcontainers)로 전체 애플리케이션을 띄우는 테스트의 부모.
 * 설정이 같아야 Spring이 컨텍스트와 컨테이너를 테스트끼리 재사용한다.
 * 올린 이미지는 개발용 uploads/가 아니라 임시 폴더에 쓴다.
 */
@SpringBootTest(properties = "app.upload.dir=${java.io.tmpdir}/blog-test-uploads")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestSupport {
}
