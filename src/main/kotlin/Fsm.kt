
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

    class Builder(private val initial: BaseState) {
        private val fsmContext = FsmContext(initial)
        val root = StateDetail(state = object : BaseState {}) // TODO: private

        fun build(): StateMachine {
            println(this) // for debug

            val allStateDetails = root.allStateDetails

            val stateToRootMap = mutableMapOf<BaseState, List<StateDetail>>()
            allStateDetails.forEach { stateDetail ->
                if (stateToRootMap.containsKey(stateDetail.state)) {
                    throw Exception("duplicate state(${stateDetail.state::class.java.simpleName}) found!")
                }

                stateToRootMap[stateDetail.state] = generateSequence(stateDetail) { it.parent }.toList()
            }

            val transitionMap = mutableMapOf<BaseState, MutableList<Transition>>()
            allStateDetails.forEach { stateDetail ->
                transitionMap[stateDetail.state] = mutableListOf()

                val stateToRoot = stateToRootMap[stateDetail.state] ?: emptyList()

                stateDetail.edges.forEach { edge ->
                    val rootToNext = stateToRootMap[edge.next]?.reversed() ?: emptyList()

                    val ignoreStatesCount = stateToRoot.intersect(rootToNext).size

                    val exitActions = stateToRoot.dropLast(ignoreStatesCount).map { it.exit }
                    val entryActions = rootToNext.drop(ignoreStatesCount).map { it.entry }
                    val actions = mutableListOf(edge.action).apply {
                        addAll(exitActions)
                        addAll(entryActions)
                    }

                    transitionMap[stateDetail.state]?.add(Transition(
                            event = edge.event,
                            guard = edge.guard,
                            next = edge.next,
                            actions = actions
                    ))
                }
            }

            // execute entry actions of initial state
            val rootToInitial = stateToRootMap[initial]?.reversed()?.drop(1) ?: emptyList()
            rootToInitial.forEach { it.entry.invoke() }

            return StateMachine(fsmContext, transitionMap)
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

class StateDetail(
        val parent: StateDetail? = null,
        val state: BaseState,
        val entry: () -> Unit = {},
        val exit: () -> Unit = {}
) {
    val children: MutableList<StateDetail> = mutableListOf()
    val edges: MutableList<Edge> = mutableListOf()

    val allStateDetails: List<StateDetail>
        get() = children.map { it.allStateDetails }.flatten() + this

    val allEdges: List<Pair<BaseState, Edge>>
        get() = children.map { it.allEdges }.flatten() + edges.map { Pair(this.state, it) }

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
            actions.forEach { it.invoke() }
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
        init: StateDetail.() -> Unit = {}
) = this.root.children.add(StateDetail(
        parent = this.root, state = state,
        entry = entry, exit = exit
).apply(init))

fun StateDetail.state(
        state: BaseState,
        entry: () -> Unit = {},
        exit: () -> Unit = {},
        init: StateDetail.() -> Unit = {}
) = this.children.add(StateDetail(
        parent = this, state = state,
        entry = entry, exit = exit
).apply(init))

@Suppress("UNCHECKED_CAST")
fun <T : BaseEvent> StateDetail.edge(
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

