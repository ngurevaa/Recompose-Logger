plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        create("recompose-logger") {
            id = "gureva.recompose.logger"
            implementationClass = "gureva.recompose.logger.gradle.RecomposeLoggerPlugin"
        }
    }
}

dependencies {
    implementation(libs.gradle.kotlin.api)
}
