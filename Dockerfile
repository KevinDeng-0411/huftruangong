# ===== Stage 1: 编译 =====
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# 阿里云镜像加速
RUN mkdir -p /root/.m2 && echo '<settings><mirrors><mirror><id>aliyun</id><url>https://maven.aliyun.com/repository/public</url><mirrorOf>central</mirrorOf></mirror></mirrors></settings>' > /root/.m2/settings.xml

# 一次全部 COPY，用 BuildKit 缓存 mount 加速重复构建
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn package -DskipTests -B -q

# ===== Stage 2: 运行 =====
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system -g app app
COPY --from=builder /build/target/*.jar app.jar
USER app
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
