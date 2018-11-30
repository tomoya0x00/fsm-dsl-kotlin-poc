import kotlin.reflect.KClass

interface BaseState
interface BaseEvent

@DslMarker
annotation class FsmDsl

class StateMachine<T : BaseState>(
        private var fsmContext: FsmContext<T>,
        private val transitionMap: Map<BaseState, List<Transition<T>>>
) {
    val currentState: T
        get() = fsmContext.state

    fun dispatch(event: BaseEvent): T {
        return fsmContext.dispatch(event, transitionMap)
    }

    @FsmDsl
    class Builder<T : BaseState>(private val initial: T) {
        private val fsmContext = FsmContext(initial)
        private val root = StateDetail<T>(state = object : BaseState {})

        fun state(
                state: T,
                entry: () -> Unit = {},
                exit: () -> Unit = {},
                init: StateDetail<T>.() -> Unit = {}
        ) = this.root.children.add(StateDetail(
                parent = this.root, state = state,
                entry = entry, exit = exit
        ).apply(init))

        fun build(): StateMachine<T> {
            println(this) // for debug

            val allStateDetails = root.allStateDetails

            val stateToRootMap = mutableMapOf<BaseState, List<StateDetail<T>>>()
            allStateDetails.forEach { stateDetail ->
                if (stateToRootMap.containsKey(stateDetail.state)) {
                    throw Exception("duplicate state(${stateDetail.state::class.java.simpleName}) found!")
                }

                stateToRootMap[stateDetail.state] = generateSequence(stateDetail) { it.parent }.toList()
            }

            val transitionMap = mutableMapOf<BaseState, MutableList<Transition<T>>>()
            allStateDetails.forEach { stateDetail ->
                transitionMap[stateDetail.state] = mutableListOf()

                val stateToRoot = stateToRootMap[stateDetail.state] ?: emptyList()

                stateDetail.edges.forEach { edge: Edge<T> ->
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

    data class Transition<T : BaseState>(
            val event: KClass<*>,
            val guard: ((BaseEvent) -> Boolean)? = null,
            val next: T,
            val actions: List<() -> Unit> = listOf()
    )
}

@FsmDsl
class StateDetail<T : BaseState>(
        val parent: StateDetail<T>? = null,
        val state: BaseState,
        val entry: () -> Unit = {},
        val exit: () -> Unit = {}
) {
    val children: MutableList<StateDetail<T>> = mutableListOf()
    val edges: MutableList<Edge<T>> = mutableListOf()

    val allStateDetails: List<StateDetail<T>>
        get() = children.map { it.allStateDetails }.flatten() + this

    fun state(
            state: T,
            entry: () -> Unit = {},
            exit: () -> Unit = {},
            init: StateDetail<T>.() -> Unit = {}
    ) = this.children.add(StateDetail(
            parent = this, state = state,
            entry = entry, exit = exit
    ).apply(init))

    @Suppress("UNCHECKED_CAST")
    fun <R : BaseEvent> edge(
            event: KClass<R>,
            guard: ((R) -> Boolean)? = null,
            next: T,
            action: () -> Unit = {}
    ) = this.edges.add(Edge(
            event = event,
            guard = guard?.let { { event: BaseEvent -> it.invoke(event as R) } },
            next = next,
            action = action
    ))

    override fun toString(): String {
        return "${state.enumNameOrClassName()}\n" +
                edges.joinToString("\n") { it.toString() }.prependIndent("  ") + "\n" +
                children.joinToString("\n") { it.toString() }.prependIndent("  ")
    }
}

class Edge<T : BaseState>(
        val event: KClass<*>,
        val guard: ((BaseEvent) -> Boolean)? = null,
        val next: T,
        val action: () -> Unit
) {
    override fun toString(): String {
        return "--> ${next.enumNameOrClassName()} : ${event.java.simpleName}"
    }
}

private fun Any.enumNameOrClassName(): String =
        if (this.javaClass.isEnum) (this as Enum<*>).name else this::javaClass.name

class FsmContext<T : BaseState>(initial: T) {

    var state: T = initial
        private set

    fun dispatch(event: BaseEvent, transitionMap: Map<BaseState, List<StateMachine.Transition<T>>>): T {
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

fun <T : BaseState> stateMachine(
        initial: T,
        init: StateMachine.Builder<T>.() -> Unit
): StateMachine<T> = StateMachine.Builder(initial = initial)
        .apply(init).build()