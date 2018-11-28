import kotlin.reflect.KClass

interface BaseState
interface BaseEvent

class StateMachine(
        private var fsmContext: FsmContext,
        private val transitionMap: Map<BaseState, List<Transition>>
) {
    fun dispatch(event: BaseEvent): BaseState {
        return fsmContext.dispatch(event, transitionMap)
    }

    class Builder(initial: BaseState) {
        private val fsmContext = FsmContext(initial)
        val root = State(state = object : BaseState {})

        fun build(): StateMachine {
            println(this) // for debug

            val transitionMap = mutableMapOf<BaseState, MutableList<Transition>>()
            root.allEdges.forEach { (bs, e) ->
                if (!transitionMap.contains(bs)) transitionMap[bs] = mutableListOf()

                val actions: MutableList<() -> Unit> = mutableListOf()

                // TODO: add exit actions

                // TODO: add entry actions

                actions.add(e.action)

                transitionMap[bs]?.add(Transition(
                        event = e.event,
                        guard = e.guard,
                        next = e.next,
                        actions = actions
                ))

                Unit
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

    data class Transition(
            val event: KClass<*>,
            val guard: ((BaseEvent) -> Boolean)? = null,
            val next: BaseState,
            val actions: List<() -> Unit> = listOf()
    )
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

class Edge(
        val event: KClass<*>,
        val guard: ((BaseEvent) -> Boolean)? = null,
        val next: BaseState,
        val action: () -> Unit
) {
    override fun toString(): String {
        return "--> ${next.javaClass.simpleName} : ${event.simpleName}"
    }
}

class FsmContext(initial: BaseState) {

    var state: BaseState = initial
        private set

    fun dispatch(event: BaseEvent, transitionMap: Map<BaseState, List<StateMachine.Transition>>): BaseState {
        val transition = transitionMap[state]?.let { transitions ->
            transitions.filter { it.event == event::class }
                    .firstOrNull { it.guard?.invoke(event) ?: true }
        }

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
): StateMachine = StateMachine.Builder(initial = initial)
        .apply(init).build()

fun StateMachine.Builder.state(
        state: BaseState,
        entry: () -> Unit = {},
        exit: () -> Unit = {},
        init: State.() -> Unit = {}
) = this.root.children.add(State(
        parent = this.root, state = state,
        entry = entry, exit = exit
).apply(init))

fun State.state(
        state: BaseState,
        entry: () -> Unit = {},
        exit: () -> Unit = {},
        init: State.() -> Unit = {}
) = this.children.add(State(
        parent = this, state = state,
        entry = entry, exit = exit
).apply(init))

@Suppress("UNCHECKED_CAST")
fun <T : BaseEvent> State.edge(
        event: KClass<T>,
        guard: ((T) -> Boolean)? = null,
        next: BaseState,
        action: () -> Unit = {}
) = this.edges.add(Edge(
        event = event,
        guard = guard?.let { { event: BaseEvent -> it.invoke(event as T) } },
        next = next,
        action = action
))

