plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}
val vaadinVersion by extra("25.1.5")
val springModulithVersion by extra("2.0.6")
val tikaVersion by extra("3.3.0")

group = "be.rubikub"
version = "0.0.1-SNAPSHOT"
description = "ExpenseLine"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("com.vaadin:vaadin-spring-boot-starter")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    developmentOnly("com.vaadin:vaadin-dev")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Source: https://mvnrepository.com/artifact/org.apache.tika/tika-core
    implementation("org.apache.tika:tika-core:$tikaVersion")
    // Source: https://mvnrepository.com/artifact/org.apache.tika/tika-parsers-standard-package
    implementation("org.apache.tika:tika-parsers-standard-package:$tikaVersion")
//    implementation("org.apache.pdfbox:pdfbox:3.0.7")

}
dependencyManagement {
    imports {
        mavenBom("com.vaadin:vaadin-bom:$vaadinVersion")
        mavenBom("org.springframework.modulith:spring-modulith-bom:$springModulithVersion")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
