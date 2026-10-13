#!/usr/bin/env bash
# 운영 데이터를 학교 MySQL(s4)에서 주 DB PostgreSQL(aigj017-recommend-db 안의 blog DB)로 옮긴다 (스텝 24, T130, research R-02).
# 실습 서버(s2)에서 지원이 한 번 실행한다. 배포(deploy.yml)가 이 파일을 ~/now-log/deploy/에 올려 둔다.
#
#   bash ~/now-log/deploy/migrate-from-mysql.sh
#
# 미리 되어 있어야 하는 것: 새 코드가 배포되어 앱이 한 번 떠서 Flyway가 blog DB에 표를 만들어 둔 것.
#
# 하는 일
#   1. 앱을 멈춘다(옮기는 동안 새로 쓰이는 데이터가 없게)
#   2. PostgreSQL 표를 비운다(앱이 빈 DB로 떠 있던 사이의 데이터와 Flyway V2의 초기 관리자 행)
#   3. 표마다 MySQL에서 꺼내 PostgreSQL에 넣는다(mysql 결과를 그대로 \copy로). 번호(id)도 그대로
#   4. 번호 시퀀스를 표마다 max(id) 다음으로 맞춘다(안 하면 다음 가입·글쓰기가 id 1과 부딪힌다)
#   5. 표마다 MySQL·PostgreSQL 행 수를 비교해 보여 준다
#   6. 행 수가 모두 같으면 앱을 다시 띄운다. 앱이 뜨면서 글 임베딩을 새로 만든다(옮기지 않음)
# 옮기지 않는 것: flyway_schema_history(DB마다 따로), post_embedding(앱이 다시 만든다), 계산 칸(DB가 다시 계산)
# MySQL은 읽기만 한다. 잘못되면 이전 커밋으로 다시 배포하면 MySQL로 돌아간다.
#
# 노트북에서 시험할 때는 아래 값을 환경 변수로 바꿔 준다(예: MYSQL_HOST=blog-mysql NETWORK=blog_default ...).
set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-s4.java21.net}"
MYSQL_PORT="${MYSQL_PORT:-13306}"
MYSQL_USER="${MYSQL_USER:-cf_u31_d1}"
MYSQL_DB="${MYSQL_DB:-cf_u31_d1}"
NETWORK="${NETWORK:-aigj017-net}"
PG="${PG:-aigj017-recommend-db}"
PG_DB="${PG_DB:-blog}"
APP="${APP:-aigj017-app}"
MYSQL_IMAGE=mysql:8.4
SKIPPED="'flyway_schema_history', 'post_embedding'"

psql_q() {
  docker exec "$PG" psql -X -q -v ON_ERROR_STOP=1 -U blog -d "$PG_DB" "$@"
}

echo "== 확인"
docker inspect "$PG" >/dev/null 2>&1 || { echo "PostgreSQL 컨테이너 $PG 가 없습니다." >&2; exit 1; }
if [ -z "$(psql_q -tAc "SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'member'")" ]; then
  echo "$PG_DB DB에 표가 없습니다. 새 코드를 먼저 배포해 앱이 한 번 뜨게(Flyway) 한 뒤 다시 실행하세요." >&2
  exit 1
fi

# MySQL 비밀번호는 화면에 보이지 않게 받고, 명령줄 대신 임시 설정 파일로 mysql에 넘긴다(ps에 찍히지 않게)
if [ -z "${MYSQL_PASSWORD:-}" ]; then
  read -r -s -p "MySQL $MYSQL_USER@$MYSQL_HOST:$MYSQL_PORT 비밀번호: " MYSQL_PASSWORD
  echo
fi
CNF_DIR="$(mktemp -d)"
trap 'rm -rf "$CNF_DIR"' EXIT
chmod 700 "$CNF_DIR"
printf '[client]\nhost=%s\nport=%s\nuser=%s\npassword="%s"\ndefault-character-set=utf8mb4\n' \
  "$MYSQL_HOST" "$MYSQL_PORT" "$MYSQL_USER" "$MYSQL_PASSWORD" > "$CNF_DIR/my.cnf"
chmod 644 "$CNF_DIR/my.cnf"

# 결과를 머리줄 없이(-N) 탭으로 나눠(-B) 이스케이프 없이(--raw) 낸다. 이스케이프는 SELECT에서 \copy 모양으로 직접 한다
mysql_q() {
  docker run --rm --network "$NETWORK" -v "$CNF_DIR:/cfg:ro" "$MYSQL_IMAGE" \
    mysql --defaults-extra-file=/cfg/my.cnf -N -B --raw "$MYSQL_DB" -e "$1"
}

mysql_q "SELECT 1" >/dev/null || { echo "MySQL에 접속하지 못했습니다." >&2; exit 1; }

TABLES=$(psql_q -tAc "SELECT table_name FROM information_schema.tables
  WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name NOT IN ($SKIPPED) ORDER BY table_name")
for t in $TABLES; do
  if [ -z "$(mysql_q "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = '$t'")" ]; then
    echo "MySQL에 $t 표가 없습니다. 옮기지 않고 멈춥니다." >&2
    exit 1
  fi
done
echo "   옮길 표 $(echo "$TABLES" | wc -l | tr -d ' ')개, MySQL $MYSQL_HOST:$MYSQL_PORT/$MYSQL_DB → PostgreSQL $PG/$PG_DB"

if [ "${1:-}" != "--yes" ]; then
  read -r -p "앱을 멈추고 $PG_DB DB의 표를 비운 뒤 옮깁니다. 계속할까요? (yes 입력) " answer
  [ "$answer" = "yes" ] || { echo "그만둡니다."; exit 1; }
fi

if docker inspect "$APP" >/dev/null 2>&1; then
  echo "== 앱 멈춤"
  docker stop "$APP" >/dev/null
fi

echo "== PostgreSQL 표 비우기"
TABLE_LIST=$(echo "$TABLES" | sed 's/.*/"&"/' | paste -sd, -)
psql_q -c "TRUNCATE $TABLE_LIST, post_embedding RESTART IDENTITY CASCADE"

# MySQL에서 칸 하나를 PostgreSQL COPY 글자 형식으로 꺼내는 식(@ 자리에 칸 이름).
# NULL은 \N, 글자 안의 \ 탭 줄바꿈은 \\ \t \n \r 로 바꾼다(역슬래시를 맨 먼저). 참/거짓(0·1)과 시각 글자는 PostgreSQL이 그대로 받는다.
# 특수 글자는 '\t' 같은 문자열 이스케이프 대신 글자 번호(92 = \, 9 = 탭, 10 = 줄바꿈, 13 = CR)로 쓴다.
# 그래야 서버의 sql_mode에 NO_BACKSLASH_ESCAPES가 있든 없든 같은 결과다
read -r -d '' CELL <<'SQL' || true
IFNULL(REPLACE(REPLACE(REPLACE(REPLACE(CAST(`@` AS CHAR), CHAR(92 USING utf8mb4), CHAR(92, 92 USING utf8mb4)), CHAR(9 USING utf8mb4), CHAR(92, 116 USING utf8mb4)), CHAR(10 USING utf8mb4), CHAR(92, 110 USING utf8mb4)), CHAR(13 USING utf8mb4), CHAR(92, 114 USING utf8mb4)), CHAR(92, 78 USING utf8mb4))
SQL

echo "== 옮기기"
for t in $TABLES; do
  # PostgreSQL 쪽 칸 순서대로, 계산 칸(GENERATED)은 빼고
  COLS=$(psql_q -tAc "SELECT column_name FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = '$t' AND is_generated = 'NEVER' ORDER BY ordinal_position")
  PG_COLS=$(echo "$COLS" | sed 's/.*/"&"/' | paste -sd, -)
  SELECT_COLS=$(for c in $COLS; do printf '%s,' "${CELL//@/$c}"; done)
  SELECT_COLS=${SELECT_COLS%,}
  # 다른 표를 가리키는 외래 키는 넣는 순서와 상관없이 받도록, 이 세션만 트리거(외래 키 확인)를 끈다. MySQL에서 이미 맞던 데이터다
  mysql_q "SELECT $SELECT_COLS FROM \`$t\`" \
    | docker exec -i -e PGOPTIONS='-c session_replication_role=replica' "$PG" \
        psql -X -q -v ON_ERROR_STOP=1 -U blog -d "$PG_DB" -c "\\copy \"$t\" ($PG_COLS) FROM STDIN"
  printf '   %s\n' "$t"
done

echo "== 번호 시퀀스 맞추기"
psql_q -tAc "SELECT table_name FROM information_schema.columns
  WHERE table_schema = 'public' AND is_identity = 'YES' AND column_name = 'id' AND table_name NOT IN ($SKIPPED)" \
  | while read -r t; do
      psql_q -tAc "SELECT setval(pg_get_serial_sequence('\"$t\"', 'id'), COALESCE(MAX(id), 0) + 1, false) FROM \"$t\"" >/dev/null
    done

echo "== 행 수 비교"
printf '   %-24s %8s %8s\n' "표" "MySQL" "Postgres"
mismatch=0
for t in $TABLES; do
  m=$(mysql_q "SELECT COUNT(*) FROM \`$t\`")
  p=$(psql_q -tAc "SELECT COUNT(*) FROM \"$t\"")
  mark=""
  if [ "$m" != "$p" ]; then mark="  ← 다름"; mismatch=1; fi
  printf '   %-24s %8s %8s%s\n' "$t" "$m" "$p" "$mark"
done

if [ "$mismatch" -ne 0 ]; then
  echo "행 수가 다른 표가 있습니다. 앱은 멈춘 채로 둡니다. 출력을 확인한 뒤 다시 실행하거나 이전 커밋으로 다시 배포하세요." >&2
  exit 1
fi

if docker inspect "$APP" >/dev/null 2>&1; then
  echo "== 앱 다시 띄움"
  since=$(date +%s)
  docker start "$APP" >/dev/null
  for _ in $(seq 1 90); do
    if docker logs --since "$since" "$APP" 2>&1 | grep -q "Started BlogApplication"; then
      echo "옮기기 끝: 행 수가 모두 같습니다. https://now-log.java21.net 에서 로그인해 확인하세요."
      echo "글 임베딩(비슷한 글·의미 검색)은 앱이 뒤에서 다시 만듭니다(글 수만큼 몇 초~몇 분)."
      exit 0
    fi
    sleep 2
  done
  echo "앱이 3분 안에 뜨지 않았습니다. docker logs --tail 80 $APP 로 확인하세요." >&2
  exit 1
fi
echo "옮기기 끝: 행 수가 모두 같습니다."
