# =====================================================================
# 后端镜像：多阶段构建
#   阶段一 maven 构建 jar（利用依赖分层缓存，改代码不必重下依赖）
#   阶段二 只带 JRE 运行，镜像体积从 ~700MB 降到 ~200MB
# =====================================================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先只拷贝 pom，单独跑一次依赖解析：只要 pom 没变，这一层就能命中缓存
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 再拷源码编译（--no-transfer-progress 减少 CI 日志噪音）
COPY src ./src
RUN mvn -B -q --no-transfer-progress clean package -DskipTests

# =====================================================================
FROM eclipse-temurin:17-jre-alpine

# tzdata 让容器内时间与 Asia/Shanghai 一致（考勤判定依赖本地时区）
RUN apk add --no-cache tzdata curl \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && addgroup -S app && adduser -S -G app app

WORKDIR /app

COPY --from=builder /build/target/teampulse-backend.jar /app/app.jar

# 日志目录需要可写
RUN mkdir -p /app/logs && chown -R app:app /app

# 以非 root 运行，降低容器逃逸后的影响面
USER app

EXPOSE 8080

# JAVA_OPTS 由 compose 注入，默认值保证单独 docker run 也能起来
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8"

HEALTHCHECK --interval=20s --timeout=5s --start-period=60s --retries=10 \
    CMD wget -q --spider http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
