# 说明：不使用 `# syntax=docker/dockerfile:1` 指令，避免构建时额外拉取语法前端镜像
# （国内网络对 auth.docker.io 的 token 接口易超时导致构建失败；
#   本文件仅使用 RUN --mount=type=cache，Docker 内置前端已稳定支持）
# ========== 第一阶段：Maven 编译构建与分层提取 ==========
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# 配置 Maven 高速镜像源（提升构建速度与稳定性）
RUN mkdir -p /root/.m2 && echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0" \
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd"> \
  <mirrors> \
    <mirror> \
      <id>aliyunmaven</id> \
      <mirrorOf>central</mirrorOf> \
      <name>Aliyun Maven</name> \
      <url>https://maven.aliyun.com/repository/public</url> \
    </mirror> \
  </mirrors> \
</settings>' > /root/.m2/settings.xml

ENV MAVEN_OPTS="-Djava.net.preferIPv4Stack=true -Dhttp.keepAlive=false -Dmaven.wagon.http.pool=false -Dmaven.wagon.http.retryHandler.count=3"

# 1. 复制依赖定义文件
COPY pom.xml .

# 2. 复制源码
COPY src ./src

# 3. 编译打包并分层提取（利用 BuildKit 挂载 /root/.m2/repository 缓存）
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn clean package -DskipTests -B && \
    JAR_FILE=$(ls target/*.jar | grep -v 'original' | head -n 1) && \
    java -Djarmode=layertools -jar "$JAR_FILE" extract --destination extracted

# ========== 第二阶段：轻量化生产运行时 ==========
FROM eclipse-temurin:21-jre-alpine
LABEL maintainer="li"

# 1. 设置时区为 Asia/Shanghai
# 2. 创建低权限用户 spring（应用容器无宿主机挂载卷，无需 su-exec 权限自愈）
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# 按变更频率由低到高从构建阶段复制分层，最大化 Docker 缓存命中率
COPY --from=builder /build/extracted/dependencies/ ./
COPY --from=builder /build/extracted/spring-boot-loader/ ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/ ./

# 暴露端口
EXPOSE 8082

# JVM 容器自适应优化参数：
# 1. 容器内存感知与动态百分比
# 2. 加快 /dev/urandom 随机数生成
# 3. 统一字符编码
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom"

# 以低权限 spring 用户运行（sh -c 展开 JAVA_OPTS），信号直达 JVM 实现优雅停机
USER spring
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
