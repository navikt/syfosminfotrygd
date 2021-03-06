import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0"

val artemisVersion = "2.6.4"
val confluentVersion = "5.3.1"
val syfooppgaveSchemasVersion = "c8be932543e7356a34690ce7979d494c5d8516d8"
val coroutinesVersion = "1.4.2"
val infotrygdForespVersion = "2019.07.29-02-53-86b22e73f7843e422ee500b486dac387a582f2d1"
val fellesformatVersion = "2019.07.30-12-26-5c924ef4f04022bbb850aaf299eb8e4464c1ca6a"
val ibmMqVersion = "9.1.0.0"
val javaxActivationVersion = "1.1.1"
val jacksonVersion = "2.12.0"
val jaxbApiVersion = "2.4.0-b180830.0359"
val jaxbVersion = "2.3.0.1"
val jedisVersion = "2.9.0"
val kafkaVersion = "2.4.0"
val kafkaEmbeddedVersion = "2.4.0"
val kluentVersion = "1.61"
val ktorVersion = "1.5.1"
val logbackVersion = "1.2.3"
val logstashEncoderVersion = "6.5"
val prometheusVersion = "0.9.0"
val spekVersion = "2.0.9"
val sykmeldingVersion = "2019.07.29-02-53-86b22e73f7843e422ee500b486dac387a582f2d1"
val cxfVersion = "3.2.7"
val jaxwsApiVersion = "2.3.1"
val commonsTextVersion = "1.4"
val jaxbBasicAntVersion = "1.11.1"
val javaxAnnotationApiVersion = "1.3.2"
val jaxwsToolsVersion = "2.3.1"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val kithHodemeldingVersion = "2019.07.30-12-26-5c924ef4f04022bbb850aaf299eb8e4464c1ca6a"
val smCommonVersion = "1.f409e4d"
val kontrollsystemblokk = "2019.07.29-02-53-86b22e73f7843e422ee500b486dac387a582f2d1"
val javaxJaxwsApiVersion = "2.2.1"
val jaxbTimeAdaptersVersion = "1.1.3"
val embeddedRedisVersion = "0.6"
val syfoXmlCodegen = "1.35193f7"
val mockkVersion = "1.10.3"

plugins {
    java
    kotlin("jvm") version "1.4.21"
    id("org.jmailen.kotlinter") version "3.3.0"
    id("com.diffplug.spotless") version "5.8.2"
    id("com.github.johnrengelman.shadow") version "6.1.0"
}

val githubUser: String by project
val githubPassword: String by project

repositories {
    mavenCentral()
    jcenter()
    maven (url= "https://dl.bintray.com/kotlin/ktor")
    maven (url= "https://dl.bintray.com/spekframework/spek-dev")
    maven (url= "http://packages.confluent.io/maven/")
    maven (url= "https://kotlin.bintray.com/kotlinx")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/syfosm-common")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }

    maven {
        url = uri("https://maven.pkg.github.com/navikt/syfo-xml-codegen")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
    maven (url = "https://oss.sonatype.org/content/groups/staging/")
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation ("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation ("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation ("io.ktor:ktor-server-netty:$ktorVersion")
    implementation ("io.ktor:ktor-client-apache:$ktorVersion")
    implementation ("io.ktor:ktor-client-cio:$ktorVersion")
    implementation ("io.ktor:ktor-client-auth-basic:$ktorVersion")
    implementation ("io.ktor:ktor-client-jackson:$ktorVersion")

    implementation ("ch.qos.logback:logback-classic:$logbackVersion")
    implementation ("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation ("com.ibm.mq:com.ibm.mq.allclient:$ibmMqVersion")

    implementation ("org.apache.kafka:kafka_2.12:$kafkaVersion")
    implementation ("io.confluent:kafka-avro-serializer:$confluentVersion")

    implementation ("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation ("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation ("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation ("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation ("no.nav.helse.xml:sm2013:$sykmeldingVersion")
    implementation ("no.nav.helse.xml:xmlfellesformat:$fellesformatVersion")
    implementation ("no.nav.helse.xml:kontrollsystemblokk:$kontrollsystemblokk")
    implementation ("no.nav.syfo.schemas:syfosmoppgave-avro:$syfooppgaveSchemasVersion")
    implementation ("no.nav.helse.xml:infotrygd-foresp:$infotrygdForespVersion")
    implementation ("no.nav.helse.xml:kith-hodemelding:$kithHodemeldingVersion")
    implementation ("no.nav.helse.xml:tssSamhandlerData:$syfoXmlCodegen")

    implementation ("no.nav.helse:syfosm-common-models:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-ws:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-rules:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-mq:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-kafka:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-networking:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-rest-sts:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-networking:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-diagnosis-codes:$smCommonVersion")

    implementation ("org.apache.commons:commons-text:$commonsTextVersion")
    implementation ("org.apache.cxf:cxf-rt-frontend-jaxws:$cxfVersion")
    implementation ("org.apache.cxf:cxf-rt-features-logging:$cxfVersion")
    implementation ("org.apache.cxf:cxf-rt-transports-http:$cxfVersion")
    implementation ("org.apache.cxf:cxf-rt-ws-security:$cxfVersion")

    implementation ("redis.clients:jedis:$jedisVersion")

    implementation ("com.migesok:jaxb-java-time-adapters:$jaxbTimeAdaptersVersion")
    implementation ("javax.xml.ws:jaxws-api:$jaxwsApiVersion")
    implementation ("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
    implementation ("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation ("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")
    implementation ("javax.activation:activation:$javaxActivationVersion")
    implementation ("com.sun.xml.ws:jaxws-tools:$jaxwsToolsVersion") {
        exclude(group = "com.sun.xml.ws", module = "policy")
    }

    testImplementation ("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation ("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty") // conflicts with WireMock
    }
    testImplementation ("io.ktor:ktor-jackson:$ktorVersion")
    testImplementation ("no.nav:kafka-embedded-env:$kafkaEmbeddedVersion")
    testImplementation ("org.apache.activemq:artemis-server:$artemisVersion")
    testImplementation ("org.apache.activemq:artemis-jms-client:$artemisVersion")
    testImplementation ("com.github.kstyrc:embedded-redis:$embeddedRedisVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")

    testImplementation ("org.spekframework.spek2:spek-dsl-jvm:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly ("org.spekframework.spek2:spek-runner-junit5:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }

}


tasks {
    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
    }

    create("printVersion") {
        doLast {
            println(project.version)
        }
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "12"
    }

    withType<ShadowJar> {
        transform(ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging {
            showStandardStreams = true
        }
    }

}
