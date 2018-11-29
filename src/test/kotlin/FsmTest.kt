import assertk.assert
import assertk.assertions.isEqualTo
import org.junit.Test

class FsmTest {

    sealed class MyState : BaseState {
        object NotLoaned : MyState()
        object OnLoan : MyState()
        object Lock : MyState()
        object UnLock : MyState()
    }

    sealed class MyEvent : BaseEvent {
        object PressRental : MyEvent()
        object PressReturn : MyEvent()
        data class PressLock(val withReturn: Boolean) : MyEvent()
        object PressUnLock : MyEvent()
    }

    @Test
    fun test() {
        val history = mutableListOf<String>()

        val sm = stateMachine(initial = MyState.NotLoaned) {
            state(MyState.NotLoaned,
                    entry = { history.add("in_NotLoaned" ) },
                    exit = { history.add("out_NotLoaned" ) }
            ) {
                edge(MyEvent.PressRental::class, next = MyState.Lock)
            }
            state(MyState.OnLoan,
                    entry = { history.add("in_OnLoan" ) },
                    exit = { history.add("out_OnLoan" ) }
            ) {
                state(MyState.Lock,
                        entry = { history.add("in_Lock" ) },
                        exit = { history.add("out_Lock" ) }
                ) {
                    edge(MyEvent.PressReturn::class, next = MyState.NotLoaned)
                    edge(MyEvent.PressUnLock::class, next = MyState.UnLock)
                }
                state(MyState.UnLock,
                        entry = { history.add("in_UnLock" ) },
                        exit = { history.add("out_UnLock" ) }
                ) {
                    edge(MyEvent.PressLock::class, guard = { !it.withReturn }, next = MyState.Lock)
                    edge(MyEvent.PressLock::class, guard = { it.withReturn }, next = MyState.NotLoaned)
                }
            }
        }

        assert(history).isEqualTo(listOf(
                "in_NotLoaned"
        ))

        history.clear()
        assert(sm.dispatch(MyEvent.PressRental)).isEqualTo(MyState.Lock)
        assert(history).isEqualTo(listOf(
                "out_NotLoaned",
                "in_OnLoan",
                "in_Lock"
        ))

        history.clear()
        assert(sm.dispatch(MyEvent.PressUnLock)).isEqualTo(MyState.UnLock)
        assert(history).isEqualTo(listOf(
                "out_Lock",
                "in_UnLock"
        ))

        history.clear()
        assert(sm.dispatch(MyEvent.PressLock(withReturn = false))).isEqualTo(MyState.Lock)
        assert(history).isEqualTo(listOf(
                "out_UnLock",
                "in_Lock"
        ))

        history.clear()
        assert(sm.dispatch(MyEvent.PressUnLock)).isEqualTo(MyState.UnLock)
        assert(history).isEqualTo(listOf(
                "out_Lock",
                "in_UnLock"
        ))

        history.clear()
        assert(sm.dispatch(MyEvent.PressLock(withReturn = true))).isEqualTo(MyState.NotLoaned)
        assert(history).isEqualTo(listOf(
                "out_UnLock",
                "out_OnLoan",
                "in_NotLoaned"
        ))

    }
}