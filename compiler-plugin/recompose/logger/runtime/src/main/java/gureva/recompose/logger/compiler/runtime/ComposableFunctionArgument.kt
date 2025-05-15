package gureva.recompose.logger.compiler.runtime

class ComposableFunctionArgument(current: Any?) {
    var current: Any? = current
        private set

    var previous: Any? = null
        private set

    fun isChanged() = current != previous

    fun setNewValue(newCurrent: Any?) {
        previous = current
        current = newCurrent
    }
}
