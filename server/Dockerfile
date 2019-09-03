FROM openjdk:10.0.2-13-jdk-slim

RUN apt update && \
    apt full-upgrade -y && \
    apt autoremove -y --purge

WORKDIR /
COPY build/libs/server-*-all.jar intellijlogger.jar
ENTRYPOINT java -jar intellijlogger.jar
