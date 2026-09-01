import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream

group = "no.nav.syfo"
version = "1.0.0"

val artemisVersion = "2.56.0"
val ibmMqVersion = "10.0.0.0"
val javaxActivationVersion = "1.1.1"
val jacksonVersion = "3.2.2"
val jaxbApiVersion = "2.4.0-b180830.0359"
val valkeyVersion = "5.5.0"
val kafkaVersion = "4.3.1"
val kluentVersion = "1.73"
val ktorVersion = "3.5.2"
val logbackVersion = "1.6.3"
val logstashEncoderVersion = "9.0"
val prometheusVersion = "0.16.0"
val kotestVersion = "6.0.4"
val jaxwsApiVersion = "2.3.1"
val javaxAnnotationApiVersion = "1.3.2"
val jaxwsToolsVersion = "2.3.1"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val jaxbTimeAdaptersVersion = "1.1.3"
val testcontainerVersion = "2.0.5"
val syfoXmlCodegen = "2.0.1"
val mockkVersion = "1.14.6"
val kotlinVersion = "2.4.10"
val ktfmtVersion = "0.56"
val opentelemetryVersion = "2.21.0"
val diagnoseVersion = "2026.1.10"

val javaVersion = JvmTarget.JVM_25

plugins {
    id("application")
    kotlin("jvm") version "2.4.10"
    id("com.gradleup.shadow") version "8.3.8"
    id("com.diffplug.spotless") version "8.10.1"
}

application {
    mainClass.set("no.nav.syfo.BootstrapKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}

dependencies {
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:$opentelemetryVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache5:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson3:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("com.ibm.mq:com.ibm.mq.jakarta.client:$ibmMqVersion")

    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")

    implementation("tools.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("tools.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("tools.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")

    implementation("no.nav.helse.xml:sm2013:$syfoXmlCodegen")
    implementation("no.nav.helse.xml:xmlfellesformat:$syfoXmlCodegen")
    implementation("no.nav.helse.xml:kontrollsystemblokk:$syfoXmlCodegen")
    implementation("no.nav.helse.xml:infotrygd-foresp:$syfoXmlCodegen")
    implementation("no.nav.helse.xml:kith-hodemelding:$syfoXmlCodegen")


    implementation("io.valkey:valkey-java:$valkeyVersion")

    implementation("com.migesok:jaxb-java-time-adapters:$jaxbTimeAdaptersVersion")
    implementation("javax.xml.ws:jaxws-api:$jaxwsApiVersion")
    implementation("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
    implementation("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")
    implementation("javax.activation:activation:$javaxActivationVersion")
    implementation("com.sun.xml.ws:jaxws-tools:$jaxwsToolsVersion")
    implementation("no.nav.tsm:diagnoser:$diagnoseVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("org.apache.activemq:artemis-jakarta-server:$artemisVersion")
    testImplementation("org.apache.activemq:artemis-jakarta-client:$artemisVersion")
    testImplementation("org.testcontainers:testcontainers:$testcontainerVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}

kotlin {
    compilerOptions {
        jvmTarget = javaVersion
    }
}


tasks {
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


    shadowJar {
        archiveBaseName.set("app")
        archiveClassifier.set("")
        isZip64 = true
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to "no.nav.syfo.BootstrapKt",
                ),
            )
        }
    }

    test {
        useJUnitPlatform {}
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
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
