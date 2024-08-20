FROM openjdk:21-jdk-slim

ARG TARGETARCH
ENV TINI_VERSION=v0.19.0
ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini-${TARGETARCH} /tini
RUN chmod +x /tini
ENTRYPOINT ["/tini", "--"]

WORKDIR /
COPY *.jar intellijlogger.jar
CMD [ "java", "-jar", "intellijlogger.jar" ]

# vim: tw=0
