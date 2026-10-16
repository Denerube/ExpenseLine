plugins {
    java
    id("io.quarkus")
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

val vaadinVersion by extra("25.1.5")
val tikaVersion by extra("3.3.0")
val langChain4JVersionBeta by extra("1.14.1-beta24")
val langChain4JVersion by extra("1.14.1")

val doclingVersion by extra("0.6.1")

group = "be.rubikub"
version = "0.0.1-SNAPSHOT"
description = "ExpenseLine"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation(platform("com.vaadin:vaadin-bom:$vaadinVersion"))

    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-jackson")
    implementation("com.vaadin:vaadin-quarkus-extension:$vaadinVersion")

    // Source: https://mvnrepository.com/artifact/org.bsc.langgraph4j/langgraph4j-core
    implementation("org.bsc.langgraph4j:langgraph4j-core:1.9.4")

    // Source: https://mvnrepository.com/artifact/dev.langchain4j/langchain4j-agentic
    implementation("dev.langchain4j:langchain4j-agentic:$langChain4JVersionBeta")

    // Source: https://mvnrepository.com/artifact/dev.langchain4j/langchain4j-http-client-jdk
    implementation("dev.langchain4j:langchain4j-http-client-jdk:$langChain4JVersion")
    runtimeOnly("dev.langchain4j:langchain4j-http-client-jdk:$langChain4JVersion")
    // Source: https://mvnrepository.com/artifact/dev.langchain4j/langchain4j-open-ai
    implementation("dev.langchain4j:langchain4j-open-ai:$langChain4JVersion")

    // Source: https://mvnrepository.com/artifact/org.apache.tika/tika-core
    implementation("org.apache.tika:tika-core:$tikaVersion")
    // Source: https://mvnrepository.com/artifact/org.apache.tika/tika-parsers-standard-package
    implementation("org.apache.tika:tika-parsers-standard-package:$tikaVersion")

    //docling
    implementation("ai.docling:docling-core:$doclingVersion")
    implementation("ai.docling:docling-serve-api:$doclingVersion")
    implementation("ai.docling:docling-serve-client:$doclingVersion")

    testImplementation("io.quarkus:quarkus-junit5")
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
