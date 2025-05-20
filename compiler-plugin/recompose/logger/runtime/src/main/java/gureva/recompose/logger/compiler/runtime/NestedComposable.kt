package gureva.recompose.logger.compiler.runtime

object ComposableFunctionStack {
    private val stack = mutableListOf<String>()

    fun push(value: String) {
        stack.add(value)
    }

    fun pop(value: String) {
        stack.remove(value)
    }

    fun getAll(): String {
        return stack.joinToString(":")
    }
}

fun enterComposable(enter: Boolean, name: String) {
    if (enter) {
        ComposableFunctionStack.push(name)
    }
    else {
        ComposableFunctionStack.pop(name)
    }
}
