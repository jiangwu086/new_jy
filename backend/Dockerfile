# ── Stage 1: 构建 ──────────────────────────────────────────────
FROM docker.m.daocloud.io/library/maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app

# 先拷贝 pom.xml，利用 Docker 层缓存加速依赖下载
COPY pom.xml .
COPY settings.xml /root/.m2/settings.xml
# BuildKit 缓存挂载 + 阿里云镜像，依赖下载稳定快速
RUN --mount=type=cache,target=/root/.m2/repository mvn dependency:go-offline -q

# 再拷贝源码并打包（跳过测试）
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository mvn package -DskipTests -q

# ── Stage 2: 运行时镜像 ────────────────────────────────────────
FROM docker.m.daocloud.io/library/eclipse-temurin:21-jre-alpine
WORKDIR /app

# 创建非 root 用户，并建好上传目录
RUN addgroup -S jy && adduser -S jy -G jy \
 && mkdir -p /app/uploads && chown -R jy:jy /app/uploads
USER jy

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Xms256m", "-Xmx512m", \
  "-jar", "app.jar"]
