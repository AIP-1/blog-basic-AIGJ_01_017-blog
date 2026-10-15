#!/usr/bin/env bash
# 실습 서버에서 블로그를 (다시) 띄운다. GitHub Actions(.github/workflows/deploy.yml)가 ssh로 접속해 실행한다.
#
# 미리 있어야 하는 것
#   ~/now-log/now-log.tar.gz : 워크플로가 올린 앱 이미지
#   환경 변수                 : 워크플로가 GitHub secrets에서 꺼내 넘긴다(아래 required)
#
# 띄우는 컨테이너 (이름은 서버의 다른 학생과 겹치지 않게 aigj017-)
#   aigj017-redis        : 로그인 토큰·시도 횟수·캐시. 같은 docker 네트워크 안에서만 닿고 서버 포트는 열지 않는다
#   aigj017-recommend-db : 주 DB PostgreSQL(pgvector). 그 안의 blog DB에 회원·글·임베딩을 모두 둔다(스텝 24).
#                          이름의 recommend는 처음에 추천 전용으로 만들어서다(볼륨 aigj017-recommend-data). 서버 포트는 열지 않는다
#   aigj017-ollama       : 임베딩 모델 서버(bge-m3, 비슷한 글·의미 검색). 서버 포트는 열지 않는다
#   aigj017-app          : 블로그 1번. 서버 포트 8460
#   aigj017-app-2        : 블로그 2번. 서버 포트 8461. 학교 nginx(now-log.java21.net)의 upstream이 두 포트로 나눠 보낸다
#
# 앱은 두 대를 늘 띄워 두고(이중화), 배포는 한 대씩 바꾼다(롤링). 한 대를 바꾸는 동안 다른 한 대가 요청을 받는다.
#   한 대 바꾸기: docker stop(처리 중인 요청·비동기 작업을 마치고 꺼짐) → 새 이미지로 띄움 → readiness가 UP일 때까지 기다림
#                → nginx가 이 앱을 다시 쓰기 시작할 때까지 기다림(fail_timeout보다 길게) → 다음 앱
#   실패하면 옛 이미지로 되돌린다. 살아 있는 앱이 0대가 되는 순간이 없게, 아직 바꾸지 않은 앱은 건드리지 않는다.
#   되돌리기는 새 버전의 DB 마이그레이션이 옛 버전과 맞을 때만 된다(컬럼 삭제·이름 바꾸기는 배포를 나눠서 한다).
#
# 백업은 하지 않는다(2026-10-13 지원 결정). docker volume rm, docker system prune --volumes를 쓰면 데이터를 잃는다
set -euo pipefail

DEPLOY_DIR="$HOME/now-log"
UPLOAD_DIR="$HOME/uploads"
NETWORK=aigj017-net
REDIS=aigj017-redis
DB=aigj017-recommend-db
DB_NAME=blog
OLLAMA=aigj017-ollama
IMAGE=aigj017/now-log:latest
# 바꾸는 순서대로 "이름:서버 포트". 처음 배포에는 2번이 없으므로 2번을 먼저 띄워야 1번을 바꾸는 동안에도 끊기지 않는다
APPS=(aigj017-app-2:8461 aigj017-app:8460)
# docker stop이 기다리는 시간. Spring은 처리 중인 HTTP 요청(최대 30초)과 비동기 작업(최대 30초)을 차례로 기다린다(AsyncConfig)
STOP_TIMEOUT=70
# nginx upstream의 fail_timeout(10초)보다 길게. 다시 뜬 앱을 nginx가 쓰기 시작하기 전에 다음 앱을 끄지 않게
NGINX_SETTLE=12

for name in RECOMMEND_DB_PASSWORD REDIS_PASSWORD JWT_SECRET; do
  if [ -z "${!name:-}" ]; then
    echo "환경 변수 $name 이 비어 있습니다. GitHub secrets에 등록했는지 확인하세요." >&2
    exit 1
  fi
done

command -v curl >/dev/null || { echo "curl이 없습니다(앱 readiness 확인에 씀)" >&2; exit 1; }

echo "== 이미지 불러오기"
gunzip -c "$DEPLOY_DIR/now-log.tar.gz" | docker load
rm -f "$DEPLOY_DIR/now-log.tar.gz"
# 태그(latest)는 새 이미지로 옮겨 가므로, 되돌릴 때는 옛 이미지를 ID로 띄운다
new_image="$(docker image inspect --format '{{.Id}}' "$IMAGE")"

echo "== 네트워크"
docker network inspect "$NETWORK" >/dev/null 2>&1 || docker network create "$NETWORK"

echo "== Redis"
# 지금 비밀번호로 PING이 되면 그대로 둔다. 다시 만들면 바꾸지 않는 동안 요청을 받는 앱이 Redis를 잃는다(로그인·캐시).
# 처음이거나 secret의 비밀번호가 바뀌었을 때만 다시 만든다(그때는 아직 옛 비밀번호인 앱이 잠깐 실패한다). 데이터는 볼륨에 남는다
if ! REDISCLI_AUTH="$REDIS_PASSWORD" docker exec -e REDISCLI_AUTH "$REDIS" redis-cli ping 2>/dev/null | grep -q PONG; then
  docker rm -f "$REDIS" >/dev/null 2>&1 || true
  docker run -d --name "$REDIS" --network "$NETWORK" --restart unless-stopped \
    -v aigj017-redis-data:/data \
    redis:7.4 redis-server --requirepass "$REDIS_PASSWORD" --appendonly yes >/dev/null
fi

echo "== PostgreSQL·Ollama"
# 둘 다 데이터(DB, 약 1.2GB 모델)를 볼륨에 두고, 이미 떠 있으면 다시 만들지 않는다(앱만 바꿔 띄운다)
if [ -z "$(docker ps -q -f "name=^${DB}$")" ]; then
  docker rm -f "$DB" >/dev/null 2>&1 || true
  # POSTGRES_PASSWORD는 이름만 넘겨 명령줄에 찍히지 않게 한다. 볼륨이 비어 있을 때(처음)만 이 값으로 계정과 recommend DB를 만든다
  POSTGRES_PASSWORD="$RECOMMEND_DB_PASSWORD" docker run -d --name "$DB" --network "$NETWORK" --restart unless-stopped \
    -e POSTGRES_DB=recommend -e POSTGRES_USER=blog -e POSTGRES_PASSWORD -e TZ=Asia/Seoul \
    -v aigj017-recommend-data:/var/lib/postgresql/data \
    pgvector/pgvector:pg17 >/dev/null
fi
# 공용 서버라 다른 학생 몫을 덜 쓰게 CPU·메모리를 묶는다(bge-m3를 올리면 2GB 안팎)
if [ -z "$(docker ps -q -f "name=^${OLLAMA}$")" ]; then
  docker rm -f "$OLLAMA" >/dev/null 2>&1 || true
  docker run -d --name "$OLLAMA" --network "$NETWORK" --restart unless-stopped \
    --cpus 4 --memory 4g \
    -v aigj017-ollama:/root/.ollama \
    ollama/ollama:latest >/dev/null
fi

for _ in $(seq 1 60); do
  docker exec "$DB" pg_isready -U blog >/dev/null 2>&1 && break
  sleep 1
done
# secret을 바꿨어도 맞도록 매번 계정 비밀번호를 secret 값으로 다시 정한다(컨테이너 안 접속은 비밀번호 없이 된다)
echo "ALTER USER blog PASSWORD :'pw';" | RPW="$RECOMMEND_DB_PASSWORD" docker exec -i -e RPW "$DB" \
  sh -c 'psql -q -v ON_ERROR_STOP=1 -U blog -d recommend -v pw="$RPW"' >/dev/null
# 주 DB(blog)가 없으면 만든다. 표는 앱이 뜰 때 Flyway가 만든다. 옛 recommend DB(스텝 22까지의 임베딩)는 건드리지 않는다
if [ -z "$(docker exec "$DB" psql -U blog -d recommend -tAc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'")" ]; then
  docker exec "$DB" createdb -U blog "$DB_NAME"
  echo "   $DB_NAME DB를 새로 만들었습니다. 운영 데이터를 옮기려면 deploy/migrate-from-mysql.sh를 실행하세요"
fi

for _ in $(seq 1 30); do
  docker exec "$OLLAMA" ollama list >/dev/null 2>&1 && break
  sleep 1
done
# 처음 한 번은 모델을 내려받는다(몇 분). 이미 있으면 바로 끝난다.
# 앱보다 먼저 받아 두어야, 앱이 뜰 때 이미 있던 글의 임베딩을 한 번에 채운다(PostEmbeddingListener.onReady)
docker exec "$OLLAMA" ollama pull bge-m3 >/dev/null

echo "== 앱"
# 소셜 로그인 키·메일 계정은 없어도 된다(그 기능만 꺼진다). 메일 비밀번호가 있으면 실제로 보내고, 없으면 로그에만 남긴다
MAIL_SENDER=log
if [ -n "${MAIL_PASSWORD:-}" ]; then
  MAIL_SENDER=smtp
fi
mkdir -p "$UPLOAD_DIR"

# 앱 하나를 띄운다: run_app 이름 서버포트 이미지
# -e 이름 (값 없이): 이 셸의 환경 변수를 그대로 넘긴다. 비밀번호가 명령줄에 찍히지 않는다
# --user: 컨테이너가 내 계정으로 돌아야 ~/uploads에 이미지를 쓸 수 있다
# 스케줄 작업(예약 발행·새벽 통계)은 두 앱 모두 돈다. 같은 글·같은 작업을 한 앱만 하도록 DB가 가른다(ScheduledPublisher, StatsAggregator)
run_app() {
  DB_PASSWORD="$RECOMMEND_DB_PASSWORD" docker run -d --name "$1" --network "$NETWORK" --restart unless-stopped \
    -p "$2:8460" \
    --user "$(id -u):$(id -g)" \
    -v "$UPLOAD_DIR:/app/uploads" \
    -e DB_URL="jdbc:postgresql://${DB}:5432/${DB_NAME}" -e DB_USERNAME=blog -e DB_PASSWORD \
    -e REDIS_HOST="$REDIS" -e REDIS_PORT=6379 -e REDIS_PASSWORD \
    -e JWT_SECRET \
    -e KAKAO_CLIENT_ID -e KAKAO_CLIENT_SECRET -e GOOGLE_CLIENT_ID -e GOOGLE_CLIENT_SECRET \
    -e MAIL_USERNAME -e MAIL_PASSWORD -e APP_MAIL_SENDER="$MAIL_SENDER" \
    -e APP_RECOMMEND_ENABLED=true \
    -e OLLAMA_URL="http://${OLLAMA}:11434" \
    -e APP_UPLOAD_DIR=/app/uploads \
    "$3" >/dev/null
}

# readiness(다 떴고 DB·Redis에 붙었는지)가 UP이 될 때까지 최대 3분: wait_ready 이름 서버포트
wait_ready() {
  for _ in $(seq 1 90); do
    if [ "$(docker inspect --format '{{.State.Running}}' "$1" 2>/dev/null)" != "true" ]; then
      echo "   $1 컨테이너가 멈췄습니다" >&2
      return 1
    fi
    if curl -fs -o /dev/null "http://127.0.0.1:$2/actuator/health/readiness"; then
      return 0
    fi
    sleep 2
  done
  echo "   $1이 3분 안에 준비되지 않았습니다" >&2
  return 1
}

# 앱 하나를 이미지로 바꾼다: replace 이름 서버포트 이미지
# docker stop은 SIGTERM을 보내고, Spring은 새 연결을 거부하고(nginx가 다른 앱으로 다시 보냄) 처리 중인 일을 마치고 꺼진다
replace() {
  if docker inspect "$1" >/dev/null 2>&1; then
    docker stop -t "$STOP_TIMEOUT" "$1" >/dev/null || true
    docker rm "$1" >/dev/null
  fi
  run_app "$1" "$2" "$3"
  wait_ready "$1" "$2"
}

# 앱 하나를 배포 전 상태로 되돌린다: rollback 이름 서버포트. 옛 이미지도 뜨지 않으면 1을 돌려준다
rollback() {
  local before="${before_image[$1]:-}"
  if [ -z "$before" ]; then
    # 이번 배포에서 처음 만든 앱(처음의 2번)이면 지운다
    echo "   $1: 배포 전에 없던 앱이라 지웁니다" >&2
    docker rm -f "$1" >/dev/null 2>&1 || true
  elif replace "$1" "$2" "$before"; then
    echo "   $1: 옛 이미지로 되돌렸습니다" >&2
  else
    echo "   $1: 옛 이미지도 뜨지 않았습니다. docker logs $1 을 확인하세요" >&2
    return 1
  fi
}

# 배포 전 각 앱이 쓰던 이미지(되돌릴 때 쓴다)
declare -A before_image
for entry in "${APPS[@]}"; do
  name="${entry%%:*}"
  before_image[$name]="$(docker inspect --format '{{.Image}}' "$name" 2>/dev/null || true)"
done

done_apps=()
for entry in "${APPS[@]}"; do
  name="${entry%%:*}"; port="${entry##*:}"
  echo "== $name (서버 포트 $port) 바꾸기"
  if ! replace "$name" "$port" "$new_image"; then
    echo "$name을 새 버전으로 띄우지 못했습니다. 마지막 로그:" >&2
    docker logs --tail 80 "$name" >&2 || true
    echo "== 되돌리기: 이번에 바꾼 앱을 거꾸로 옛 버전으로" >&2
    # 되돌리기가 실패하면 그 앱은 죽어 있다. 이때 남은 앱(새 버전)까지 되돌리려고 끄면 살아 있는 앱이 0대가 되므로 그대로 둔다
    if rollback "$name" "$port"; then
      for (( i=${#done_apps[@]}-1; i>=0; i-- )); do
        if ! rollback "${done_apps[$i]%%:*}" "${done_apps[$i]##*:}"; then
          break
        fi
      done
    else
      echo "   이미 새 버전으로 바꾼 앱(${done_apps[*]:-없음})은 요청을 받고 있으므로 건드리지 않습니다" >&2
    fi
    exit 1
  fi
  done_apps+=("$entry")
  echo "   준비됨. nginx가 다시 쓰기 시작할 때까지 ${NGINX_SETTLE}초"
  sleep "$NGINX_SETTLE"
done

echo "배포 완료: https://now-log.java21.net (nginx를 거치지 않고 보려면 http://s2.java21.net:8460, :8461)"
# 두 앱이 모두 새 버전이 된 뒤에야 옛 이미지를 지운다(그 전에는 되돌릴 때 쓴다).
# docker image prune은 다른 학생 이미지까지 지울 수 있어 쓰지 않는다. 쓰는 컨테이너가 있으면 rmi가 알아서 거절한다
for old in $(printf '%s\n' "${before_image[@]}" | sort -u); do
  if [ -n "$old" ] && [ "$old" != "$new_image" ]; then
    docker rmi "$old" >/dev/null 2>&1 || true
  fi
done
