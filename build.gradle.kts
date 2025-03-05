import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream

group = "no.nav.syfo"
version = "1.0.0"

val artemisVersion = "2.39.0"
val coroutinesVersion = "1.10.1"
val ibmMqVersion = "9.4.2.0"
val javaxActivationVersion = "1.1.1"
val jacksonVersion = "2.18.3"
val jaxbApiVersion = "2.4.0-b180830.0359"
val valkeyVersion = "5.3.0"
val kafkaVersion = "3.9.0"
val kluentVersion = "1.73"
val ktorVersion = "3.1.1"
val logbackVersion = "1.5.17"
val logstashEncoderVersion = "8.0"
val prometheusVersion = "0.16.0"
val kotestVersion = "5.9.1"
val jaxwsApiVersion = "2.3.1"
val javaxAnnotationApiVersion = "1.3.2"
val jaxwsToolsVersion = "2.3.1"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val jaxbTimeAdaptersVersion = "1.1.3"
val testcontainersVersion = "1.20.6"
val syfoXmlCodegen = "2.0.1"
val mockkVersion = "1.13.17"
val kotlinVersion = "2.1.10"
val ktfmtVersion = "0.44"
val opentelemetryVersion = "2.13.3"

val javaVersion = JvmTarget.JVM_21


//Added due to vulnerabilities
val nettycommonVersion = "4.1.119.Final"
val commonsCompressVersion = "1.27.1"
val commonsCodecVersion = "1.18.0"

plugins {
    id("application")
    kotlin("jvm") version "2.1.10"
    id("com.gradleup.shadow") version "8.3.6"
    id("com.diffplug.spotless") version "7.0.2"
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
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:$opentelemetryVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    constraints {
        implementation("io.netty:netty-common:$nettycommonVersion") {
            because("Due to vulnerabilities in io.ktor:ktor-server-netty")
        }
    }
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    constraints {
        implementation("commons-codec:commons-codec:$commonsCodecVersion") {
            because("override transient from io.ktor:ktor-client-apache")
        }
    }
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("com.ibm.mq:com.ibm.mq.jakarta.client:$ibmMqVersion")

    implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

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
    implementation("com.sun.xml.ws:jaxws-tools:$jaxwsToolsVersion") {
        exclude(group = "com.sun.xml.ws", module = "policy")
    }

    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("org.apache.activemq:artemis-jakarta-server:$artemisVersion")
    testImplementation("org.apache.activemq:artemis-jakarta-client:$artemisVersion")
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    constraints {
        implementation("org.apache.commons:commons-compress:$commonsCompressVersion") {
            because("Due to vulnerabilities, see CVE-2024-26308")
        }
    }
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
