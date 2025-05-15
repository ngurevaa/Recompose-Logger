package gureva.recompose.logger.compiler.runtime

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NoLiveLiterals
import androidx.compose.runtime.remember

@NoLiveLiterals
@Composable
fun RecomposeLogger(
    name: String,
    startTime: Long,
    endTime: Long,
    arguments: Map<String, Any?>
) {
    val recomposeLog = StringBuilder()

    for ((argumentName, argumentValue) in arguments) {
        val dataDiff = remember { ComposableFunctionArgument(argumentValue) }
        dataDiff.setNewValue(argumentValue)

        if (dataDiff.isChanged()) {
            val previous = dataDiff.previous
            val current = dataDiff.current
            recomposeLog.append("\t$argumentName changed: previous = $previous, current = $current\n")
        }
    }

    val isEnabled = RecomposeLoggerConfig.isEnabled
    if (recomposeLog.isNotEmpty() && isEnabled) {
        Log.i("RecomposeLogger", "$name recomposed. Reason for now:")
        Log.i("RecomposeLogger", "${recomposeLog}\n")
        Log.i("RecomposeLogger", "Recomposition time: ${endTime - startTime} ns\n ")
    }
}
