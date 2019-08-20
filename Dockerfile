FROM navikt/java:11
COPY build/libs/*.jar app.jar
ENV JAVA_OPTS="-Dlogback.configurationFile=logback-remote.xml"
ENV HTTP_PROXY="http://webproxy.nais:8088"
ENV http_proxy="http://webproxy.nais:8088"
ENV HTTPS_PROXY="http://webproxy.nais:8088"
ENV https_proxy="http://webproxy.nais:8088"
ENV NO_PROXY="localhost,127.0.0.1,10.254.0.1,.local,.adeo.no,.nav.no,.aetat.no,.devillo.no,.oera.no,.nais.io,kafka-schema-registry.tpa"
ENV no_proxy="localhost,127.0.0.1,10.254.0.1,.local,.adeo.no,.nav.no,.aetat.no,.devillo.no,.oera.no,.nais.io,kafka-schema-registry.tpa"
ENV JAVA_PROXY_OPTIONS="-Dhttp.proxyHost=webproxy.nais -Dhttps.proxyHost=webproxy.nais -Dhttp.proxyPort=8088 -Dhttps.proxyPort=8088 -Dhttp.nonProxyHosts=localhost|127.0.0.1|10.254.0.1|*.local|*.adeo.no|*.nav.no|*.aetat.no|*.devillo.no|*.oera.no|*.nais.io|kafka-schema-registry.tpa"