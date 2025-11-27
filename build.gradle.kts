plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    application
}

group = "com.yunhalee"
version = "0.0.1-SNAPSHOT"
description = "github_mcp"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val ktorVersion = "3.3.2"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // MCP SDK
    implementation("io.modelcontextprotocol:kotlin-sdk:0.8.0")

    // Ktor
    implementation("io.ktor:ktor-client-cio:${ktorVersion}")
    implementation("io.ktor:ktor-client-content-negotiation:${ktorVersion}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${ktorVersion}")

    // IO
    implementation("com.squareup.okio:okio:3.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.5.4")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.16")

    // Test
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

application {
    mainClass.set("com.yunhalee.github_mcp.McpServerKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.yunhalee.github_mcp.McpServerKt"
    }
    // Fat JAR 생성
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}