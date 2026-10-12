# 블로그 앱 이미지. GitHub Actions(.github/workflows/deploy.yml)가 만들어 실습 서버로 보낸다.
# 프론트(React) → Spring Boot jar → 실행용 JRE 세 단계로 나눠, 마지막 이미지에는 jar와 JRE만 남긴다.

# 1. 프론트 빌드 (scripts/build-frontend.sh와 같은 일)
FROM node:24-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2. jar 빌드. 프론트 결과를 static에 넣으면 jar 하나에 담긴다
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q -B dependency:go-offline
COPY src src
COPY --from=frontend /frontend/dist src/main/resources/static
# 테스트는 Testcontainers(docker)가 필요해 여기서는 건너뛴다. 워크플로가 이미지를 만들기 전에 따로 돌린다
RUN ./mvnw -q -B -DskipTests package && cp target/blog-*.jar app.jar

# 3. 실행
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /app/app.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod TZ=Asia/Seoul
EXPOSE 8460
ENTRYPOINT ["java", "-jar", "app.jar"]
