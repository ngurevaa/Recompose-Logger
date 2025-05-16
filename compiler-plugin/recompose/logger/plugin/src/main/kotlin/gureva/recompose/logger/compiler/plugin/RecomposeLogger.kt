package gureva.recompose.logger.compiler.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.serialization.mangle.ir.isAnonymous
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrRawFunctionReference
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.util.toIrConst
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.popLast
import java.util.concurrent.atomic.AtomicInteger

internal class RecomposeLogger(
    private val pluginContext: IrPluginContext,
    private val logModifierChanges: Boolean,
    private val logFunctionChanges: Boolean,
    private val messageCollector: MessageCollector
) : IrElementTransformerVoid() {

    private var currentFunction: FunctionInfo? = null

    private val nanoTimeFunctionSymbol by lazy {
        pluginContext.referenceClass(
            systemClassId
        )?.owner?.functions?.singleOrNull {
            it.name.asString() == NANO_TIME_FUNCTION_NAME
        }?.symbol
    }

    private val recomposeLoggerFunctionSymbol by lazy {
        pluginContext.referenceFunctions(
            recomposeLoggerCallableId
        ).first()
    }

    override fun visitFunction(declaration: IrFunction): IrStatement {
        currentFunction = FunctionInfo(declaration, currentFunction)

        if (declaration.isComposable()) {
            val statements = declaration.body?.statements
            val modifiedStatements = mutableListOf<IrStatement>()

            modifiedStatements += createDepthChangeCall(declaration.name.asString(), true)
            if (statements != null) modifiedStatements += statements
            modifiedStatements += createDepthChangeCall(declaration.name.asString(), false)

            val body: IrBlockBody = declaration.body as IrBlockBody
            body.statements.clear()
            body.statements.addAll(modifiedStatements)
        }

        val result = super.visitFunction(declaration)
        currentFunction = currentFunction?.parent
        return result
    }

    private fun createTimeVariable(name: String, function: IrFunction): IrVariable {
        return IrVariableImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            origin = IrDeclarationOrigin.DEFINED,
            symbol = IrVariableSymbolImpl(),
            name = Name.identifier(name),
            type = pluginContext.irBuiltIns.longType,
            isVar = false,
            isConst = false,
            isLateinit = false
        ).apply {
            initializer = nanoTimeFunctionSymbol?.let {
                IrCallImpl(
                    startOffset = UNDEFINED_OFFSET,
                    endOffset = UNDEFINED_OFFSET,
                    type = pluginContext.irBuiltIns.longType,
                    symbol = it,
                    typeArgumentsCount = 0,
                    valueArgumentsCount = 0
                )
            }
            parent = function
        }
    }

    private val filesStack = mutableListOf<IrFile>()

    override fun visitFile(declaration: IrFile): IrFile {
        filesStack += declaration
        val result = super.visitFile(declaration)
        filesStack.popLast()
        return result
    }

    override fun visitBlockBody(body: IrBlockBody): IrBody {
        val lastFile = filesStack.lastOrNull()
        val lastFunctionInfo = currentFunction?.searchNotAnonymous()

        if (lastFile == null || lastFunctionInfo == null) return super.visitBlockBody(body)

        val lastFunction = lastFunctionInfo.function
        if (lastFunction.name.isSpecial || lastFunction.isInline) return super.visitBlockBody(body)

        val transformedBody = processBlockBody(body, lastFunction)
        return super.visitBlockBody(transformedBody)
    }

    private fun processBlockBody(body: IrBlockBody, function: IrFunction): IrBlockBody {
        val processedStatements = processStatements(body.statements, function)
        body.statements.clear()
        body.statements.addAll(processedStatements)
        return body
    }

    private fun createDepthChangeCall(function: String, isEnter: Boolean): IrCall {
        return IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = pluginContext.irBuiltIns.unitType,
            symbol = pluginContext.referenceFunctions(
                CallableId(
                    FqName("gureva.recompose.logger.compiler.runtime"),
                    Name.identifier("enterComposable")
                )
            ).single(),
            typeArgumentsCount = 0,
            valueArgumentsCount = 2
        ).apply {
            putValueArgument(0, isEnter.toIrConst(pluginContext.irBuiltIns.booleanType))
            putValueArgument(1, function.toIrConst(pluginContext.irBuiltIns.stringType))
//            putValueArgument(1,
//                if (isEnter) {
//                    IrGetValueImpl(
//                        UNDEFINED_OFFSET, UNDEFINED_OFFSET,
//                        pluginContext.irBuiltIns.intType,
//                        createComposableDepthVariable().symbol
//                    )
//                } else {
//                    IrGetValueImpl(
//                        UNDEFINED_OFFSET, UNDEFINED_OFFSET,
//                        pluginContext.irBuiltIns.intType,
//                        createComposableDepthVariable(decrement = true).symbol
//                    )
//                }
//            )
//            val x = 1
//            putValueArgument(1, x.toIrConst(pluginContext.irBuiltIns.intType))
        }
    }

    private fun processStatements(
        statements: List<IrStatement>,
        outerFunction: IrFunction
    ): MutableList<IrStatement> {
        val modifiedStatements = mutableListOf<IrStatement>()
        for (statement in statements) {
            when (statement) {
                is IrBlock -> {
                    val newStatements = processStatements(statement.statements, outerFunction)
                    modifiedStatements += newStatements
                }

                is IrCall -> {
                    if (!statement.isRecomposeLoggerFunction() && statement.isValidComposableCall()) {
                        val startTime = createTimeVariable("startTime", outerFunction)
                        val endTime = createTimeVariable("endTime", outerFunction)

                        val (logger, variables) = createRecomposeLoggerCall(
                            outerFunction,
                            statement,
                            startTime,
                            endTime
                        )

                        modifiedStatements += variables
                        modifiedStatements += startTime
                        modifiedStatements += statement
                        modifiedStatements += endTime
                        modifiedStatements += logger
                    } else {
                        modifiedStatements += statement
                    }
                }

                else -> {
                    modifiedStatements += statement
                }
            }
        }
        return modifiedStatements
    }

    private fun createRecomposeLoggerCall(
        outerFunction: IrFunction,
        call: IrCall,
        startTime: IrVariable,
        endTime: IrVariable,

        ): Pair<IrCallImpl, MutableList<IrVariable>> {
        val variables = mutableListOf<IrVariable>()

        val arguments = mutableMapOf<String, IrExpression>()

        for (index in 0 until call.valueArgumentsCount) {
            call.modifyArgument(index, outerFunction, variables, arguments)
        }

        return IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = pluginContext.irBuiltIns.unitType,
            symbol = recomposeLoggerFunctionSymbol,
            typeArgumentsCount = 0,
            valueArgumentsCount = 4
        ).apply {
            putValueArgument(0, call.symbol.owner.name.asString().toIrConst(pluginContext.irBuiltIns.stringType))
            putValueArgument(1, IrGetValueImpl(
                startOffset = SYNTHETIC_OFFSET,
                endOffset = SYNTHETIC_OFFSET,
                type = startTime.type,
                symbol = startTime.symbol
            ))
            putValueArgument(2, IrGetValueImpl(
                startOffset = SYNTHETIC_OFFSET,
                endOffset = SYNTHETIC_OFFSET,
                type = endTime.type,
                symbol = endTime.symbol
            ))
            putValueArgument(3, createRecomposeLoggerArgumentsExpression(arguments))
        } to variables
    }

    private fun IrCall.modifyArgument(
        index: Int,
        outerFunction: IrFunction,
        variables: MutableList<IrVariable>,
        arguments: MutableMap<String, IrExpression>
    ) {
        val parameter = symbol.owner.valueParameters[index]
        val expression = getValueArgument(index) ?: return
        if ((parameter.isComposeModifier() && !logModifierChanges) || (expression.isFunctionExpression() && (!logFunctionChanges))) return
        if (expression.isFunctionExpression()) {
            if (canTrackFunctionArgument(symbol.owner, expression, parameter)) {
                val variable = handleFunctionExpressionArgument(
                    parameter,
                    expression,
                    outerFunction,
                    this,
                    index,
                )
                variables += variable
                arguments[parameter.name.asString()] = IrGetValueImpl(
                    expression.startOffset,
                    expression.endOffset,
                    expression.type,
                    variable.symbol,
                )
            }
        } else {
            arguments[parameter.name.asString()] = expression.deepCopySavingMetadata(outerFunction)
        }
    }

    private class FunctionInfo(
        val function: IrFunction,
        val parent: FunctionInfo? = null,
    ) {

        fun searchNotAnonymous(): FunctionInfo? {
            if (!function.name.isAnonymous) return this

            return parent?.searchNotAnonymous()
        }
    }

    private fun canTrackFunctionArgument(
        function: IrSimpleFunction,
        expression: IrExpression,
        parameter: IrValueParameter
    ): Boolean {
        if (!function.isInline) return true
        if (parameter.isNoinline) return true

        return when (expression) {
            is IrFunctionReference -> !expression.symbol.owner.isInline
            is IrRawFunctionReference -> !expression.symbol.owner.isInline
            is IrFunctionExpression -> false
            else -> false
        }
    }

    private fun handleFunctionExpressionArgument(
        parameter: IrValueParameter,
        expression: IrExpression,
        outerFunction: IrFunction,
        call: IrCall,
        index: Int,
    ): IrVariableImpl {
        val variable = IrVariableImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            origin = IrDeclarationOrigin.DEFINED,
            symbol = IrVariableSymbolImpl(),
            name = Name.identifier("$${parameter.name.asString()}${expression.startOffset}${counter.incrementAndGet()}"),
            type = parameter.type,
            isVar = false,
            isConst = false,
            isLateinit = false
        )
        variable.parent = outerFunction
        variable.initializer = expression
        call.putValueArgument(
            index, IrGetValueImpl(
                expression.startOffset,
                expression.endOffset,
                expression.type,
                variable.symbol,
            )
        )
        return variable
    }

    private fun createRecomposeLoggerArgumentsExpression(
        arguments: Map<String, IrExpression>,
    ): IrExpression {
        val irBuiltIns = pluginContext.irBuiltIns
        val nullableAnyType = irBuiltIns.anyType.makeNullable()
        val stringType = irBuiltIns.stringType

        val argumentPairType = pluginContext.referenceClass(pairClassId)
            ?.typeWith(stringType, nullableAnyType) ?: nullableAnyType
        val pairConstructorCall = pluginContext.referenceConstructors(pairClassId)
            .first { it.owner.valueParameters.size == 2 }

        val mapOfSymbol = pluginContext.referenceFunctions(mapOfCallableId)
            .first { it.owner.valueParameters.size == 1 && it.owner.valueParameters.first().isVararg }
        val argumentsMapType = irBuiltIns.mapClass.typeWith(stringType, nullableAnyType)

        return IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = argumentsMapType,
            symbol = mapOfSymbol,
            typeArgumentsCount = 2,
            valueArgumentsCount = 1
        ).apply {
            putTypeArgument(0, stringType)
            putTypeArgument(1, nullableAnyType)
            putValueArgument(
                0, IrVarargImpl(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    irBuiltIns.arrayClass.typeWith(argumentPairType),
                    argumentPairType,
                    arguments.map { (name, expression) ->
                        IrConstructorCallImpl(
                            UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                            argumentPairType,
                            pairConstructorCall,
                            typeArgumentsCount = 2,
                            constructorTypeArgumentsCount = 0,
                            valueArgumentsCount = 2
                        ).apply {
                            putTypeArgument(0, stringType)
                            putTypeArgument(1, nullableAnyType)
                            putValueArgument(0, name.toIrConst(stringType))
                            putValueArgument(1, expression)
                        }
                    })
            )
        }
    }

    private fun IrCall.isValidComposableCall(): Boolean {
        val callFunctionOwner = symbol.owner
        val callFunctionName = callFunctionOwner.name
        return !callFunctionName.isSpecial
                && callFunctionOwner.isComposable()
                && valueArgumentsCount != 0
                && callFunctionOwner.returnType.isUnit()
                && callFunctionOwner.valueParameters.any { it.isMainComposeModifier() }
    }

    private fun IrValueParameter.isMainComposeModifier(): Boolean =
        name.asString() == "modifier" && type.isComposeModifier()

    private fun IrFunction.isComposable(): Boolean = hasAnnotation(Composable)

    private fun IrValueParameter.isComposeModifier(): Boolean = type.isComposeModifier()

    private fun IrType.isComposeModifier(): Boolean = classFqName?.asString() == MODIFIER_FULL

    private fun IrExpression.isFunctionExpression(): Boolean =
        this is IrFunctionExpression || this is IrFunctionReference || this is IrRawFunctionReference

    private val counter = AtomicInteger()

    private companion object {
        val Composable = FqName("androidx.compose.runtime.Composable")
        const val COMPOSE_UI_PACKAGE = "androidx.compose.ui"
        const val MODIFIER_FULL = "$COMPOSE_UI_PACKAGE.Modifier"
        const val NANO_TIME_FUNCTION_NAME = "nanoTime"

        val pairClassId = ClassId(
            FqName("kotlin"),
            Name.identifier("Pair")
        )

        val systemClassId = ClassId(FqName("java.lang"), Name.identifier("System"))

        val mapOfCallableId = CallableId(
            FqName("kotlin.collections"),
            Name.identifier("mapOf")
        )

        val recomposeLoggerCallableId = CallableId(
            FqName("gureva.recompose.logger.compiler.runtime"),
            Name.identifier("RecomposeLogger")
        )

        fun IrCall.isRecomposeLoggerFunction(): Boolean {
            return symbol.owner.fqNameWhenAvailable == recomposeLoggerCallableId.asSingleFqName()
        }
    }
}
