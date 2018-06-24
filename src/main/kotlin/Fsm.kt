interface BaseState
interface BaseEvent

// TODO: support context
// TODO: detect errors at compile time
// TODO: reconsidering how to write edges

class StateMachine(
        private var fsmContext: FsmContext,
        private val stateMap: Map<BaseState, State>
) {
    fun dispatch(event: BaseEvent): BaseState {
        return fsmContext.dispatch(event, stateMap)
    }

    class Builder(initial: BaseState) {
        val fsmContext = FsmContext(initial)
        val root = State(state = object : BaseState {})

        fun build(): StateMachine {
            println(this) // for debug
            val stateMap = mutableMapOf<BaseState, State>()
            root.allChildren.forEach { (bs, s) ->
                // TODO: detect duplicate state
                stateMap[bs] = s
            }

            return StateMachine(fsmContext, stateMap)
        }

        override fun toString(): String {
            return "StateMachine\n" +
                    root.children.joinToString("\n") { it.toString() }.prependIndent("  ")
        }
    }
}

class State(
        val parent: State? = null,
        val state: BaseState,
        val entry: () -> Unit = {},
        val exit: () -> Unit = {}
) {
    val children: MutableList<State> = mutableListOf()
    val edges: MutableList<Edge> = mutableListOf()

    val allChildren: List<Pair<BaseState, State>>
        get() = children.map { it.allChildren }.flatMap { it } + children.map { Pair(it.state, it) }

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

    fun dispatch(event: BaseEvent, stateMap: Map<BaseState, State>): BaseState {
        val prev = state

       state = stateMap[prev]?.let {
            it.edges.firstOrNull { it.event == event }?.next
        } ?: prev

        return state
    }
}

fun stateMachine(
        initial: BaseState,
        init: StateMachine.Builder.() -> Unit
): StateMachine = StateMachine.Builder(initial = initial).apply(init).build()

fun StateMachine.Builder.state(
        state: BaseState,
        entry: () -> Unit = {},
        exit: () -> Unit = {},
        init: State.() -> Unit = {}
) = this.root.children.add(State(parent = this.root, state = state, entry = entry, exit = exit).apply(init))

fun State.state(
        state: BaseState,
        entry: () -> Unit = {},
        exit: () -> Unit = {},
        init: State.() -> Unit = {}
) = this.children.add(State(parent = this, state = state, entry = entry, exit = exit).apply(init))

fun State.edge(
        event: BaseEvent,
        next: BaseState,
        action: () -> Unit = {}
) = this.edges.add(Edge(event = event, next = next, action = action))