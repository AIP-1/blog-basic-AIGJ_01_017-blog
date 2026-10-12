#!/usr/bin/env bash
# 실습 서버에서 블로그를 (다시) 띄운다. GitHub Actions(.github/workflows/deploy.yml)가 ssh로 접속해 실행한다.
#
# 미리 있어야 하는 것
#   ~/now-log/now-log.tar.gz : 워크플로가 올린 앱 이미지
#   환경 변수                 : 워크플로가 GitHub secrets에서 꺼내 넘긴다(아래 required)
#
# 띄우는 컨테이너 (이름은 서버의 다른 학생과 겹치지 않게 aigj017-)
#   aigj017-redis : 로그인 토큰·시도 횟수·캐시. 같은 docker 네트워크 안에서만 닿고 서버 포트는 열지 않는다
#   aigj017-app   : 블로그. 서버 포트 8460(개인 포트 첫 번호) → 학교 nginx(now-log.java21.net)가 여기로 넘긴다
set -euo pipefail

DEPLOY_DIR="$HOME/now-log"
UPLOAD_DIR="$HOME/uploads"
NETWORK=aigj017-net
REDIS=aigj017-redis
APP=aigj017-app
IMAGE=aigj017/now-log:latest
PORT=8460

for name in DB_ADDRESS DB_PORT DB_NAME DB_USERNAME DB_PASSWORD REDIS_PASSWORD JWT_SECRET; do
  if [ -z "${!name:-}" ]; then
    echo "환경 변수 $name 이 비어 있습니다. GitHub secrets에 등록했는지 확인하세요." >&2
    exit 1
  fi
done

echo "== 이미지 불러오기"
old_image="$(docker image inspect --format '{{.Id}}' "$IMAGE" 2>/dev/null || true)"
gunzip -c "$DEPLOY_DIR/now-log.tar.gz" | docker load
rm -f "$DEPLOY_DIR/now-log.tar.gz"

echo "== 네트워크"
docker network inspect "$NETWORK" >/dev/null 2>&1 || docker network create "$NETWORK"

echo "== Redis"
# 비밀번호가 바뀌었을 수 있으니 매번 새로 만든다. 데이터는 볼륨(aigj017-redis-data)에 남는다
docker rm -f "$REDIS" >/dev/null 2>&1 || true
docker run -d --name "$REDIS" --network "$NETWORK" --restart unless-stopped \
  -v aigj017-redis-data:/data \
  redis:7.4 redis-server --requirepass "$REDIS_PASSWORD" --appendonly yes >/dev/null

echo "== 앱"
mkdir -p "$UPLOAD_DIR"
docker rm -f "$APP" >/dev/null 2>&1 || true
# -e 이름 (값 없이): 이 셸의 환경 변수를 그대로 넘긴다. 비밀번호가 명령줄에 찍히지 않는다
# --user: 컨테이너가 내 계정으로 돌아야 ~/uploads에 이미지를 쓸 수 있다
docker run -d --name "$APP" --network "$NETWORK" --restart unless-stopped \
  -p "$PORT:8460" \
  --user "$(id -u):$(id -g)" \
  -v "$UPLOAD_DIR:/app/uploads" \
  -e DB_URL="jdbc:mysql://${DB_ADDRESS}:${DB_PORT}/${DB_NAME}?connectionTimeZone=Asia/Seoul&characterEncoding=UTF-8" \
  -e DB_USERNAME -e DB_PASSWORD \
  -e REDIS_HOST="$REDIS" -e REDIS_PORT=6379 -e REDIS_PASSWORD \
  -e JWT_SECRET \
  -e APP_UPLOAD_DIR=/app/uploads \
  "$IMAGE" >/dev/null

echo "== 뜰 때까지 기다림 (최대 3분)"
for _ in $(seq 1 90); do
  if [ "$(docker inspect --format '{{.State.Running}}' "$APP")" != "true" ]; then
    echo "앱 컨테이너가 멈췄습니다. 마지막 로그:" >&2
    docker logs --tail 80 "$APP" >&2
    exit 1
  fi
  if docker logs "$APP" 2>&1 | grep -q "Started BlogApplication"; then
    echo "배포 완료: http://now-log.java21.net (nginx 적용 전에는 http://s2.java21.net:$PORT)"
    # 이번에 바뀐 옛 이미지만 지운다(docker image prune은 다른 학생 이미지까지 지울 수 있어 쓰지 않는다)
    new_image="$(docker image inspect --format '{{.Id}}' "$IMAGE")"
    if [ -n "$old_image" ] && [ "$old_image" != "$new_image" ]; then
      docker rmi "$old_image" >/dev/null 2>&1 || true
    fi
    exit 0
  fi
  sleep 2
done
echo "3분 안에 뜨지 않았습니다. 마지막 로그:" >&2
docker logs --tail 80 "$APP" >&2
exit 1
