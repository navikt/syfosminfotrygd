FROM navikt/java:11
COPY build/libs/syfosminfotrygd-*-all.jar app.jar
ENV JAVA_OPTS='-Dlogback.configurationFile=logback-remote.xml'
ENV APPLICATION_PROFILE="remote"
