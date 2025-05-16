plugins {
    id("java-library")
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
}

publishing {
    repositories {
        mavenLocal()
    }
    publications {
        create<MavenPublication>("RecomposeLogger") {
            groupId = "gureva.recompose.logger"
            artifactId = "compiler-plugin"
            version = "1.0.18"
            from(components["java"])
        }
    }
}
