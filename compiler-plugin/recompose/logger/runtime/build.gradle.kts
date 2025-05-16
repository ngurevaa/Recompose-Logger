plugins {
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.library")
    `maven-publish`
}

android {
    namespace = "gureva.recompose.logger"
    compileSdk = 33

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
}

publishing {
    repositories {
        mavenLocal()
    }
    publications {
        create<MavenPublication>("RecomposeLoggerRuntime") {
            groupId = "gureva.recompose.logger"
            artifactId = "compiler-runtime"
            version = "1.0.19"
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
