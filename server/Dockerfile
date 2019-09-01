FROM openjdk:10.0.2-13-jdk-slim
COPY build/libs/server-*-all.jar /intellijlogger.jar
WORKDIR /
ENTRYPOINT ["java", "-jar", "intellijlogger.jar"]
