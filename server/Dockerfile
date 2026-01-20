FROM eclipse-temurin:21-jdk-jammy

ARG TARGETARCH
ENV TINI_VERSION=v0.19.0
ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini-${TARGETARCH} /tini
RUN chmod +x /tini
ENTRYPOINT ["/tini", "--"]

RUN apt update && apt install -y procps libjemalloc2 && rm -rf /var/lib/apt/lists/*
RUN cp /usr/lib/`arch`-linux-gnu/libjemalloc.so.2 /usr/lib/
ENV LD_PRELOAD=/usr/lib/libjemalloc.so.2

WORKDIR /
COPY *.jar intellijlogger.jar
CMD [ "java", "-jar", "intellijlogger.jar" ]

# vim: tw=0
