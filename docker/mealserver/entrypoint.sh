#!/bin/sh
# entrypoint.sh - Backend container startup script

set -e

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
