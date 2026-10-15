# now-log

티스토리형 멀티 블로그 서비스(지원). 누구나 가입해 자기 블로그(`blog.com/@주소`)를 열고, 에디터로 글을 발행하고, 다른 블로그 글을 읽고 댓글·공감한다.

운영: https://now-log.java21.net

이 저장소는 과제 제출용 [AIP-1/blog-basic-AIGJ_01_017-blog](https://github.com/AIP-1/blog-basic-AIGJ_01_017-blog)에서 커밋 기록째 옮겨 왔다(2026-10-14). 커밋 메시지의 `Merge pull request #1`~`#39`는 그 저장소의 PR이고, PR 본문과 리뷰는 blog-docs `history/pull-requests/`에 옮겨 두었다.

명세·계획·작업 목록·학습 자료는 별도 문서 저장소(blog-docs)에 있다. 옆 폴더 `../blog-docs`의 `specs/001-tistory-blog/`를 본다.

| 문서 | 내용 |
| --- | --- |
| `spec.md` | 기능 명세(수용 시나리오) |
| `contracts/rest-api.md` | REST API |
| `data-model.md`, `erd/schema.sql` | 데이터 모델, 테이블 |
| `tasks.md` | 작업 목록과 구현 스텝 |
| `quickstart.md` | 로컬 실행과 손으로 확인하는 시나리오 |
| `learning/` | 스텝별 학습 자료(문서 저장소 맨 위, 파일 이름은 한글) |

## 기술

- 백엔드: Java 21, Spring Boot 4.1(Web MVC, Security, Data JPA, Validation, Cache, Data Redis, Flyway), Maven
- DB: PostgreSQL 17 + pgvector(주 데이터와 글 임베딩, 스텝 23에서 MySQL을 합침), Redis 7.4(캐시·연타 방지·시도 제한)
- 임베딩: Ollama + bge-m3(로컬)
- 프론트: React + TypeScript + Vite, Tiptap 에디터. 빌드 결과를 jar 안 `static/`에 넣어 한 서버로 배포한다
- 테스트: JUnit 5, MockMvc, Testcontainers(PostgreSQL pgvector·Redis), Vitest

## 실행

모두 이 저장소 맨 위 폴더에서.

```bash
docker compose up -d                 # PostgreSQL(pgvector), Redis, Ollama
./scripts/build-frontend.sh          # 프론트를 빌드해 src/main/resources/static으로 복사
./mvnw spring-boot:run               # http://blog.test:8080 (처음 뜰 때 Flyway가 테이블 생성)
```

- 플랫폼은 `http://blog.test:8080`, 블로그는 그 아래 `http://blog.test:8080/@{주소}`다(스텝 21). `/etc/hosts`에 `127.0.0.1 blog.test` 한 줄이면 된다(`quickstart.md`).
- Ollama는 이미 만들어 둔 docker 볼륨 `ollama`를 쓴다(`docker-compose.yml`의 `external: true`). 없으면 `docker volume create ollama` 뒤 `docker exec blog-ollama ollama pull bge-m3`.
- Ollama가 꺼져 있어도 서버는 뜬다. 그동안 쓴 글은 비슷한 글 추천만 비어 있고(의미 검색은 503 안내), 다음에 서버가 뜰 때 채운다.

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
| PostgreSQL | `localhost:5432`, DB `blog` | `blog` / `blog` |
| Redis | `localhost:6379` | 없음 |
| Ollama | `http://localhost:11434` | 없음 |
| 서비스 관리자 | `blog.test:8080/login` | `admin@blog.test` / `admin1234!` (Flyway `V2__admin_account.sql`, 운영 전에 바꿀 것) |

운영 환경 변수: `DB_URL`(`jdbc:postgresql://…`), `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `OLLAMA_URL`, `APP_RECOMMEND_ENABLED`, `JWT_SECRET`, `APP_UPLOAD_DIR`, `APP_PLATFORM_DOMAIN`. 실습 서버에서는 `deploy/deploy.sh`가 GitHub secrets로 채운다. 앱은 두 대(`aigj017-app` 8460, `aigj017-app-2` 8461)를 띄우고 학교 nginx upstream이 나눠 보내며, 배포는 한 대씩 바꾸고 `/actuator/health/readiness`가 UP인지 본 뒤 다음 앱으로 넘어간다(롤링, 실패하면 옛 이미지로 되돌림). 운영 데이터를 MySQL에서 옮길 때는 `deploy/migrate-from-mysql.sh`(스텝 24).

## 배포용 jar

```bash
./scripts/build-frontend.sh && ./mvnw clean package     # target/blog-*.jar
java -jar target/blog-*.jar --spring.profiles.active=prod
```

## 구조

```
src/main/java/com/nhnacademy/blog/
  global/
    config/       설정(보안, 캐시, 비동기, 화면 주소 처리 SpaForwardController)
    host/         블로그 주소 해석(`/@주소`, API의 `X-Blog-Address` 헤더 → @CurrentBlog)
    visibility/   글·블로그 가시성 판단(PostVisibilityPolicy, 목록용 PostSpecifications)
    auth/         다른 기능이 쓰는 인증 입구(LoginMember, LoginMembers, BlogOwnerGuard, 정지 안내)
    security/     본문 HTML 정화, CSP 헤더
    error/ entity/ web/   오류 응답, 생성·수정 시각, 페이지·커서·중복 요청 방지 같은 웹 도구
  auth/           로그인 속(JWT, 쿠키, 필터, 비밀번호 해시, 시도 제한, 소셜 로그인)
  member/ blog/ post/ category/ tag/ comment/ reaction/ bookmark/ image/
  subscription/ notification/ search/ recommend/ home/ ranking/ manage/ admin/
src/main/resources/db/migration/   Flyway(V1__init.sql = blog-docs erd/schema.sql)
frontend/src/                      React 앱(app/ 라우터, pages/, components/, api/)
deploy/                            서버에서 실행하는 배포 스크립트
```

### 기능 패키지 안의 세 계층

```
post/
  presentation/   PostController, dto/        요청을 받아 응답 DTO로
       ↓
  application/    PostReadService ...         트랜잭션, 업무 규칙
       ↓
  domain/         Post, PostRepository ...    엔티티, enum, Repository
```

의존은 위에서 아래로만 간다. 여러 기능이 같이 쓰는 것은 `global/`에 둔다. 아래 규칙은 `ArchitectureTest`(ArchUnit)가 `./mvnw test` 때마다 검사한다.

- `domain`은 `application`·`presentation`을 모른다.
- `application`은 `presentation`을 모른다.
- 인증의 속(토큰, 쿠키, 필터, 비밀번호 해시 등)은 `auth/` 밖에서 쓰지 않는다. 다른 기능은 `global/auth/`의 입구와 `auth`의 `CredentialService`·`LoginSessions`만 쓴다.

### 한 요청이 지나가는 길: 글 읽기

브라우저가 `https://now-log.java21.net/@hjiwon/5`를 열면 두 단계를 거친다.

```mermaid
sequenceDiagram
  participant B as 브라우저(React)
  participant S as 화면 주소 처리<br/>SpaForwardController
  participant F as 필터<br/>CSRF 헤더 · JWT 쿠키
  participant C as PostController
  participant V as PostReadService<br/>→ PostVisibilityPolicy
  B->>S: GET /@hjiwon/5
  S->>S: 블로그·글 확인
  S-->>B: index.html(공유 미리보기 태그) 또는 404 · 301
  B->>F: GET /api/posts/5 + X-Blog-Address: hjiwon
  F->>C: 쿠키의 JWT로 로그인한 회원(없으면 비회원)
  C->>V: @CurrentBlog(hjiwon), 글 5, 보는 사람
  V-->>C: 보임 / 404 / 구독 안내
  C-->>B: 글 상세 JSON(공감·북마크 여부 포함)
```

1. **화면 주소 단계** (`/api` 밖): 서버가 블로그와 글을 먼저 확인한다. 없거나 볼 수 없으면 404 상태로, 다른 블로그 소속 글이면 서버가 직접 301로 보낸다. 볼 수 있으면 React 앱(`index.html`)을 준다. 비회원도 볼 수 있는 글이면 head에 공유 미리보기(Open Graph) 태그도 넣는다.
2. **API 단계**: React가 `/api/posts/5`를 부르며 `X-Blog-Address` 헤더로 블로그를 알린다.
   - 필터가 상태를 바꾸는 요청(POST 등)이면 `X-Requested-With` 헤더를 확인하고(CSRF 방지), 쿠키의 JWT로 로그인한 회원을 정한다.
   - `@CurrentBlog`가 헤더를 블로그로 바꾼다.
   - `PostVisibilityPolicy`가 판단한다: ① 글이 있나 → ② 이 블로그 소속인가 → ③ 주인인가 → ④ 남이 볼 수 있나(공개 범위, 구독자 공개).
   - 볼 수 없으면 로그인 여부와 상관없이 404다. API에서는 다른 블로그 글도 301이 아니라 404다.
3. 본문을 보여 준 뒤 화면이 `POST /api/posts/5/views`로 조회를 남긴다(같은 사람이 5분 안에 다시 보면 세지 않음).

글을 저장하면 커밋된 뒤에 `PostEmbeddingListener`가 다른 스레드에서 Ollama로 임베딩을 만들어 pgvector에 넣는다. 비슷한 글 추천과 의미 검색은 이 임베딩을 쓴다. 저장 응답은 임베딩 계산을 기다리지 않는다.
