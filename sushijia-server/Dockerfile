FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY sushijia-common/pom.xml sushijia-common/pom.xml
COPY sushijia-framework/pom.xml sushijia-framework/pom.xml
COPY sushijia-repository/pom.xml sushijia-repository/pom.xml
COPY sushijia-ai/pom.xml sushijia-ai/pom.xml
COPY sushijia-hotel/pom.xml sushijia-hotel/pom.xml
COPY sushijia-admin/pom.xml sushijia-admin/pom.xml
RUN mvn -q -DskipTests dependency:go-offline

COPY . .
ARG MODULE=sushijia-hotel
RUN mvn -U -q -Dmaven.test.skip=true -pl ${MODULE} -am package

FROM eclipse-temurin:17-jre
WORKDIR /app

ARG MODULE=sushijia-hotel
ENV TZ=Asia/Shanghai
COPY --from=build /workspace/${MODULE}/target/${MODULE}-1.0.0.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
