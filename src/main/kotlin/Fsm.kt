interface BaseState
interface BaseEvent

// TODO: support context
// TODO: detect errors at compile time
// TODO: reconsidering how to write edges

class StateMachine(private val initial: BaseState) {

    fun dispatch(event: BaseEvent): BaseState {
        // TODO: return new state
        return initial
    }
}

class State(state: BaseState, entry: () -> Unit, exit: () -> Unit)
class Edge(event: BaseEvent, next: BaseState, action: () -> Unit)

fun stateMachine(
        initial: BaseState,
        init: StateMachine.() -> Unit
): StateMachine = StateMachine(initial = initial).apply(init)

fun StateMachine.state(
        state: BaseState,
        entry: () -> Unit = {},
        exit: () -> Unit = {},
        init: State.() -> Unit = {}
) = State(state = state, entry = entry, exit = exit).apply(init)

fun State.state(
        state: BaseState,
        entry: () -> Unit = {},
        exit: () -> Unit = {},
        init: State.() -> Unit = {}
) = State(state = state, entry = entry, exit = exit).apply(init)

fun State.edge(
        event: BaseEvent,
        next: BaseState,
        action: () -> Unit = {},
        init: Edge.() -> Unit = {}
) = Edge(event = event, next = next, action = action).apply(init)