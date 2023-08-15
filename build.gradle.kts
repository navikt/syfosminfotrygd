import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream

group = "no.nav.syfo"
version = "1.0.0"

val artemisVersion = "2.30.0"
val coroutinesVersion = "1.7.3"
val infotrygdForespVersion = "1.0.3"
val fellesformatVersion = "1.0.3"
val ibmMqVersion = "9.3.3.0"
val javaxActivationVersion = "1.1.1"
val jacksonVersion = "2.15.2"
val jaxbApiVersion = "2.4.0-b180830.0359"
val jaxbVersion = "2.3.0.1"
val jedisVersion = "4.4.3"
val kafkaVersion = "3.5.1"
val kluentVersion = "1.73"
val ktorVersion = "2.3.3"
val logbackVersion = "1.4.11"
val logstashEncoderVersion = "7.4"
val prometheusVersion = "0.16.0"
val kotestVersion = "5.6.2"
val jaxwsApiVersion = "2.3.1"
val jaxbBasicAntVersion = "1.11.1"
val javaxAnnotationApiVersion = "1.3.2"
val jaxwsToolsVersion = "2.3.1"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val smCommonVersion = "1.0.11"
val javaxJaxwsApiVersion = "2.2.1"
val jaxbTimeAdaptersVersion = "1.1.3"
val testcontainersVersion = "1.18.3"
val syfoXmlCodegen = "1.0.3"
val mockkVersion = "1.13.5"
val kotlinVersion = "1.9.0"
val commonsCodecVersion = "1.16.0"
val ktfmtVersion = "0.44"

plugins {
    kotlin("jvm") version "1.9.0"
    id("com.diffplug.spotless") version "6.20.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.cyclonedx.bom") version "1.7.4"
}

val githubUser: String by project
val githubPassword: String by project

repositories {
    mavenCentral()
    maven (url= "https://packages.confluent.io/maven/")
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
}

dependencies {
    implementation ("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation ("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation ("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation ("io.ktor:ktor-server-core:$ktorVersion")
    implementation ("io.ktor:ktor-server-netty:$ktorVersion")
    implementation ("io.ktor:ktor-client-core:$ktorVersion")
    implementation ("io.ktor:ktor-client-apache:$ktorVersion")
    implementation ("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation ("io.ktor:ktor-serialization-jackson:$ktorVersion")

    implementation("commons-codec:commons-codec:$commonsCodecVersion")
    // override transient version 1.10 from io.ktor:ktor-client-apache

    implementation ("ch.qos.logback:logback-classic:$logbackVersion")
    implementation ("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation ("com.ibm.mq:com.ibm.mq.allclient:$ibmMqVersion")

    implementation ("org.apache.kafka:kafka_2.12:$kafkaVersion")

    implementation ("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation ("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation ("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation ("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation ("no.nav.helse.xml:sm2013:$syfoXmlCodegen")
    implementation ("no.nav.helse.xml:xmlfellesformat:$syfoXmlCodegen")
    implementation ("no.nav.helse.xml:kontrollsystemblokk:$syfoXmlCodegen")
    implementation ("no.nav.helse.xml:infotrygd-foresp:$syfoXmlCodegen")
    implementation ("no.nav.helse.xml:kith-hodemelding:$syfoXmlCodegen")

    implementation ("no.nav.helse:syfosm-common-models:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-mq:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-kafka:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-networking:$smCommonVersion")

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

    testImplementation ("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation ("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation ("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation ("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation ("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation ("org.apache.activemq:artemis-server:$artemisVersion") {
        exclude(group = "commons-collections", module = "commons-collections")
        exclude(group = "org.apache.commons", module = "commons-configuration2")
    }
    testImplementation ("org.apache.activemq:artemis-jms-client:$artemisVersion")
    testImplementation ("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation ("io.mockk:mockk:$mockkVersion")
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

    withType<ShadowJar> {
        transform(ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
    }

    withType<Test> {
        useJUnitPlatform {
        }
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    register<JavaExec>("generateRuleMermaid") {
        val output = ByteArrayOutputStream()
        mainClass.set("no.nav.syfo.rules.common.GenerateMermaidKt")
        classpath = sourceSets["main"].runtimeClasspath
        group = "documentation"
        description = "Generates mermaid diagram source of rules"
        standardOutput = output
        doLast {
            val readme = File("README.md")
            val lines = readme.readLines()

            val starterTag = "<!-- RULE_MARKER_START -->"
            val endTag = "<!-- RULE_MARKER_END -->"

            val start = lines.indexOfFirst { it.contains(starterTag) }
            val end = lines.indexOfFirst { it.contains(endTag) }

            val newLines: List<String> =
                lines.subList(0, start) +
                        listOf(
                            starterTag,
                        ) +
                        output.toString().split("\n") +
                        listOf(
                            endTag,
                        ) +
                        lines.subList(end + 1, lines.size)
            readme.writeText(newLines.joinToString("\n"))
        }
    }

    spotless {
        kotlin { ktfmt(ktfmtVersion).kotlinlangStyle() }
        check {
            dependsOn("spotlessApply")
            dependsOn("generateRuleMermaid")
        }
    }

}
