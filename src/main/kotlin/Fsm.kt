interface BaseState
interface BaseEvent

class StateMachine(
        private var fsmContext: FsmContext,
        private val transitionMap: Map<BaseState, Map<BaseEvent, Transition>>
) {
    fun dispatch(event: BaseEvent): BaseState {
        return fsmContext.dispatch(event, transitionMap)
    }

    class Builder(initial: BaseState) {
        private val fsmContext = FsmContext(initial)
        val root = State(state = object : BaseState {})

        fun build(): StateMachine {
            println(this) // for debug

            val transitionMap = mutableMapOf<BaseState, MutableMap<BaseEvent, Transition>>()
            root.allEdges.forEach { (bs, e) ->
                if (!transitionMap.contains(bs)) transitionMap[bs] = mutableMapOf()

                // TODO: detect duplicate event
                // TODO: generate all action list
                val actions: List<() -> Unit> = listOf(e.action)
                transitionMap[bs]?.set(e.event, Transition(next = e.next, actions = actions))
            }

            return StateMachine(fsmContext, transitionMap)
        }

        private fun breadthFirstSearch(state: BaseState, root: State): Boolean {
            val markedStates = mutableListOf<BaseState>()
            val queue = mutableListOf<State>()

            markedStates += root.state
            queue += root
            while (!queue.isEmpty()) {
                val v = queue.removeAt(0)
                if (v.state == state) return true
                v.children.forEach { chState ->
                    if (markedStates.none { it == chState.state }) {
                        markedStates += chState.state
                        queue += chState
                    }
                }
            }

            return false
        }

        override fun toString(): String {
            return "StateMachine\n" +
                    root.children.joinToString("\n") { it.toString() }.prependIndent("  ")
        }
    }

    data class Transition(val next: BaseState, val actions: List<() -> Unit> = listOf())
}

class State(
        val parent: State? = null,
        val state: BaseState,
        val entry: () -> Unit = {},
        val exit: () -> Unit = {}
) {
    val children: MutableList<State> = mutableListOf()
    val edges: MutableList<Edge> = mutableListOf()

    val allEdges: List<Pair<BaseState, Edge>>
        get() = children.map { it.allEdges }.flatMap { it } + edges.map { Pair(this.state, it) }

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

    fun dispatch(event: BaseEvent, transitionMap: Map<BaseState, Map<BaseEvent, StateMachine.Transition>>): BaseState {
        val transition = transitionMap[state]?.let { it[event] }
        transition?.run {
            actions.forEach { it() }
            state = next
        }

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