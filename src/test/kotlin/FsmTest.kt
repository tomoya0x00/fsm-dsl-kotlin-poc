import assertk.assert
import assertk.assertions.*
import org.junit.Test

class FsmTest {

    sealed class MyState : BaseState {
        object StateA : MyState()
        object StateB : MyState()
        object StateC : MyState()
    }

    sealed class MyEvent : BaseEvent {
        object EventA : MyEvent()
        object EventB : MyEvent()
    }

    @Test
    fun test() {
        val sm = stateMachine(initial = MyState.StateA) {
            state(MyState.StateA, entry = {}, exit = {}) {

                edge(MyEvent.EventA, next = MyState.StateC) { /* action */ }

                state(MyState.StateB, entry = {}, exit = {})
            }

            state(MyState.StateC, entry = {}, exit = {}) {
                edge(MyEvent.EventB, next = MyState.StateA) { /* action */ }
            }
        }

        val next = sm.dispatch(MyEvent.EventA)
        assert(next).isEqualTo(MyState.StateC)
    }
}