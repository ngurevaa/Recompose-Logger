package gureva.recompose.logger.compiler.runtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object RecomposeLoggerConfig {
    var isEnabled by mutableStateOf(true)
}
