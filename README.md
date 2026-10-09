# blog-basic-AIGJ_01_017-blog

티스토리형 멀티 블로그 서비스(지원). 누구나 가입해 자기 블로그(서브도메인)를 열고, 에디터로 글을 발행하고, 다른 블로그 글을 읽고 댓글·공감한다.

명세·계획·작업 목록·학습 자료는 별도 문서 저장소(blog-docs)에 있다. 옆 폴더 `../blog-docs`의 `specs/001-tistory-blog/`를 본다.

| 문서 | 내용 |
| --- | --- |
| `spec.md` | 기능 명세(수용 시나리오) |
| `contracts/rest-api.md` | REST API |
| `data-model.md`, `erd/schema.sql` | 데이터 모델, 테이블 |
| `tasks.md` | 작업 목록과 구현 스텝 |
| `quickstart.md` | 로컬 실행과 손으로 확인하는 시나리오 |
| `learning/` | 스텝별 학습 자료 |

## 기술

- 백엔드: Java 21, Spring Boot 4.1(Web MVC, Security, Data JPA, Validation, Cache, Data Redis, Flyway), Maven
- DB: MySQL 8.4(주 데이터), Redis 7.4(캐시·연타 방지·시도 제한), PostgreSQL 17 + pgvector(비슷한 글 추천 임베딩)
- 임베딩: Ollama + bge-m3(로컬)
- 프론트: React + TypeScript + Vite, Tiptap 에디터. 빌드 결과를 jar 안 `static/`에 넣어 한 서버로 배포한다
- 테스트: JUnit 5, MockMvc, Testcontainers(MySQL·Redis·pgvector), Vitest

## 실행

모두 이 저장소 맨 위 폴더에서.

```bash
docker compose up -d                 # MySQL, Redis, PostgreSQL(pgvector), Ollama
./scripts/build-frontend.sh          # 프론트를 빌드해 src/main/resources/static으로 복사
./mvnw spring-boot:run               # http://blog.test:8080 (처음 뜰 때 Flyway가 테이블 생성)
```

- 블로그 주소(`{주소}.blog.test:8080`)를 열려면 `*.blog.test`가 127.0.0.1을 가리켜야 한다. dnsmasq 설정 또는 `/etc/hosts`는 `quickstart.md`를 본다.
- Ollama는 이미 만들어 둔 docker 볼륨 `ollama`를 쓴다(`docker-compose.yml`의 `external: true`). 없으면 `docker volume create ollama` 뒤 `docker exec blog-ollama ollama pull bge-m3`.
- Ollama가 꺼져 있어도 서버는 뜬다. 그동안 쓴 글은 비슷한 글 추천만 비어 있고, 다음에 서버가 뜰 때 채운다.

프론트를 고치면서 볼 때는 개발 서버를 쓴다(`/api`를 8080으로 넘김).

```bash
cd frontend && npm install && npm run dev     # http://blog.test:5173
```

## 테스트

```bash
./mvnw test                  # Docker가 켜져 있어야 한다(Testcontainers가 임시 DB를 띄움)
cd frontend && npm test      # Vitest
```

테스트는 위 개발 DB를 건드리지 않는다. 임베딩은 진짜 모델 대신 가짜를 쓴다.

## 개발용 접속 정보

`docker-compose.yml`, `src/main/resources/application-dev.yml`의 개발용 값이다. 운영(prod 프로필)에서는 모두 환경 변수로 준다.

| 대상 | 주소 | 계정 |
| --- | --- | --- |
| MySQL | `localhost:3306`, DB `blog` | `blog` / `blog` (root / `root`) |
| PostgreSQL | `localhost:5432`, DB `recommend` | `blog` / `blog` |
| Redis | `localhost:6379` | 없음 |
| Ollama | `http://localhost:11434` | 없음 |
| 서비스 관리자 | `blog.test:8080/login` | `admin@blog.test` / `admin1234!` (Flyway `V2__admin_account.sql`, 운영 전에 바꿀 것) |

운영 환경 변수: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `RECOMMEND_DB_URL`, `RECOMMEND_DB_USERNAME`, `RECOMMEND_DB_PASSWORD`, `OLLAMA_URL`, `JWT_SECRET`, `APP_UPLOAD_DIR`, `APP_PLATFORM_DOMAIN`.

## 배포용 jar

```bash
./scripts/build-frontend.sh && ./mvnw clean package     # target/blog-*.jar
java -jar target/blog-*.jar --spring.profiles.active=prod
```

## 구조

```
src/main/java/com/nhnacademy/blog/
  global/        공통(설정, 오류 응답, 인증, 서브도메인 해석, 가시성 판단, 웹 도구)
  auth/ member/ blog/ post/ category/ tag/ comment/ reaction/ image/
  search/ home/ manage/ recommend/ subscription/ admin/
    각 기능 안: domain/(엔티티·Repository) → application/(Service) → presentation/(Controller, dto/)
src/main/resources/db/migration/   주 DB(MySQL) Flyway
src/main/resources/db/recommend/   추천 DB(PostgreSQL) Flyway
frontend/src/                      React 앱(app/ 라우터, pages/, components/, api/)
```
