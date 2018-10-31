FROM navikt/java:11
COPY build/install/* /app
ENV JAVA_OPTS="'-Dlogback.configurationFile=logback-remote.xml'"
ENTRYPOINT ["/app/bin/syfosminfotrygd"]
ENV APPLICATION_PROFILE="remote"