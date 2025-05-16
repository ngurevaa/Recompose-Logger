package gureva.recompose.logger.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class RecomposeLoggerPlugin : KotlinCompilerPluginSupportPlugin {

    override fun apply(target: Project) {
        target.extensions.create(EXTENSION_NAME, RecomposeLoggerExtension::class.java)
        target.applyRuntimeDependency()
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project

        return project.provider {
            listOf(
                SubpluginOption("enabled", project.isPluginEnabled().toString()),
                SubpluginOption("logModifierChanges", project.logModifierChanges().toString()),
                SubpluginOption("logFunctionChanges", project.logFunctionChanges().toString()),
            )
        }
    }

    override fun getCompilerPluginId(): String = "gureva.recompose.logger.compiler-plugin"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "gureva.recompose.logger",
        artifactId = "compiler-plugin",
        version = "1.0.18"
    )

    private fun Project.applyRuntimeDependency() = afterEvaluate {
        if (isPluginEnabled()) {
            dependencies {
                add("implementation", "gureva.recompose.logger:compiler-runtime:1.0.19")
            }
        }
    }

    private fun Project.isPluginEnabled(): Boolean = getExtension().isEnabled

    private fun Project.logModifierChanges(): Boolean = getExtension().logModifierChanges
    private fun Project.logFunctionChanges(): Boolean = getExtension().logFunctionChanges

    private fun Project.getExtension(): RecomposeLoggerExtension {
        return project.extensions.findByType(RecomposeLoggerExtension::class.java)
            ?: project.extensions.create(EXTENSION_NAME, RecomposeLoggerExtension::class.java)
    }

    companion object {
        private const val EXTENSION_NAME = "recomposeLogger"
    }

}
