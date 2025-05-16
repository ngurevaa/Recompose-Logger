package gureva.recompose.logger.compiler.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class RecomposeLoggerIrGeneration(
    private val logModifierChanges: Boolean,
    private val logFunctionChanges: Boolean,
    private val messageCollector: MessageCollector
) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) =
        moduleFragment.transformChildrenVoid(
            RecomposeLogger(
                pluginContext,
                logModifierChanges,
                logFunctionChanges,
                messageCollector,
            )
        )
}
