#!/bin/sh
# entrypoint.sh - Backend container startup script

set -e

# 创建日志目录并设置权限
if [ ! -d /app/logs ]; then
    mkdir -p /app/logs
fi

# 尝试设置日志目录权限（如果是挂载的，可能因宿主机权限而失败）
chmod 777 /app/logs 2>/dev/null || echo "Warning: Cannot change permissions for /app/logs (may be a mounted volume)"

# 解决日志中文乱码
export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Duser.language=zh -Duser.country=CN"
export LANG=C.UTF-8
export LC_ALL=C.UTF-8

# JVM tuning
JAVA_OPTS="${JAVA_OPTS:-}"
JAVA_OPTS="${JAVA_OPTS} -Xms${BACKEND_JVM_HEAP_MIN:-256m}"
JAVA_OPTS="${JAVA_OPTS} -Xmx${BACKEND_JVM_HEAP_MAX:-512m}"
JAVA_OPTS="${JAVA_OPTS} -XX:+UseG1GC"
JAVA_OPTS="${JAVA_OPTS} -XX:+HeapDumpOnOutOfMemoryError"
JAVA_OPTS="${JAVA_OPTS} -XX:HeapDumpPath=/home/eladmin/"
JAVA_OPTS="${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom"

echo "=========================================="
echo "Starting eladmin backend"
echo "Profile   : ${SPRING_PROFILES_ACTIVE:-prod}"
echo "DB Host   : ${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo "Redis Host: ${REDIS_HOST}:${REDIS_PORT}"
echo "JVM Heap  : ${BACKEND_JVM_HEAP_MIN} - ${BACKEND_JVM_HEAP_MAX}"
echo "=========================================="

exec java ${JAVA_OPTS} -jar app.jar \
  --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} \
  --spring.datasource.druid.driverClassName=com.mysql.cj.jdbc.Driver \
  --spring.datasource.druid.url="jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?serverTimezone=Asia/Shanghai&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true" \
  --spring.datasource.druid.username=${DB_USER} \
  --spring.datasource.druid.password=${DB_PWD} \
  --spring.redis.host=${REDIS_HOST} \
  --spring.redis.port=${REDIS_PORT} \
  --spring.redis.password=${REDIS_PASSWORD} \
  --spring.redis.database=${REDIS_DB:-1} \
  "$@"
