interface BaseState
interface BaseEvent

// TODO: support context
// TODO: detect errors at compile time
// TODO: reconsidering how to write edges

class StateMachine(initial: BaseState) {

    private val fsmContext = FsmContext(initial)
    val children: MutableList<State> = mutableListOf()

    fun dispatch(event: BaseEvent): BaseState {
        // TODO: return new state
        return fsmContext.dispatch(event, children)
    }

    override fun toString(): String {
        return "StateMachine\n" +
                children.joinToString("\n") { it.toString() }.prependIndent("  ")
    }
}

class State(val state: BaseState, val entry: () -> Unit, val exit: () -> Unit) {
    val children: MutableList<State> = mutableListOf()
    val edges: MutableList<Edge> = mutableListOf()

    override fun toString(): String {
        return "${state.javaClass.simpleName}\n" +
                edges.joinToString("\n") { it.toString() }.prependIndent("  ") + "\n" +
                children.joinToString("\n") { it.toString() }.prependIndent("  ")
    }
}

class Edge(val event: BaseEvent, val next: BaseState, val action: () -> Unit) {
    override fun toString(): String {
        return "--> ${next.javaClass.simpleName} : ${event.javaClass.simpleName}"
    }
}

class FsmContext(initial: BaseState) {

    var state: BaseState = initial
        private set

    fun dispatch(event: BaseEvent, children: List<State>): BaseState {
        val prev = state
        val next = findEdgeRecursive(children, event)?.next ?: prev
        state = next

        return state
    }

    // TODO: tailrec
    private fun findEdgeRecursive(states: List<State>, event: BaseEvent): Edge? {
        return states.findEdge(event) ?: kotlin.run {
            var edge: Edge? = null
            states.forEach {
                edge = findEdgeRecursive(it.children, event)
                if (edge != null) return@run edge
            }
            return@run edge
        }
    }

    private fun List<State>.findEdge(event: BaseEvent): Edge? =
            this.flatMap { it.edges }.firstOrNull { it.event == event }
}

fun stateMachine(
        initial: BaseState,
        init: StateMachine.() -> Unit
): StateMachine = StateMachine(initial = initial).apply(init)

fun StateMachine.state(
        state: BaseState,
        entry: () -> Unit = {},
        exit: () -> Unit = {},
        init: State.() -> Unit = {}
) = this.children.add(State(state = state, entry = entry, exit = exit).apply(init))

fun State.state(
        state: BaseState,
        entry: () -> Unit = {},
        exit: () -> Unit = {},
        init: State.() -> Unit = {}
) = this.children.add(State(state = state, entry = entry, exit = exit).apply(init))

fun State.edge(
        event: BaseEvent,
        next: BaseState,
        action: () -> Unit = {}
) = this.edges.add(Edge(event = event, next = next, action = action))