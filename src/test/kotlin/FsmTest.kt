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
        val sm = stateMachine(initial = MyState.NotLoaned) {
            state(MyState.NotLoaned,
                    entry = { println("entry NotLoaned") },
                    exit = { println("exit NotLoaned") }
            ) {
                edge(MyEvent.PressRental::class, next = MyState.Lock)
            }
            state(MyState.OnLoan,
                    entry = { println("entry OnLoan") },
                    exit = { println("exit OnLoan") }
            ) {
                state(MyState.Lock,
                        entry = { println("entry Lock") },
                        exit = { println("exit Lock") }
                ) {
                    edge(MyEvent.PressReturn::class, next = MyState.NotLoaned)
                    edge(MyEvent.PressUnLock::class, next = MyState.UnLock)
                }
                state(MyState.UnLock,
                        entry = { println("entry UnLock") },
                        exit = { println("exit UnLock") }
                ) {
                    edge(MyEvent.PressLock::class, guard = { !it.withReturn }, next = MyState.Lock)
                    edge(MyEvent.PressLock::class, guard = { it.withReturn }, next = MyState.NotLoaned)
                }
            }
        }

        assert(sm.dispatch(MyEvent.PressRental)).isEqualTo(MyState.Lock)
        assert(sm.dispatch(MyEvent.PressUnLock)).isEqualTo(MyState.UnLock)
        assert(sm.dispatch(MyEvent.PressLock(withReturn = false))).isEqualTo(MyState.Lock)
        assert(sm.dispatch(MyEvent.PressUnLock)).isEqualTo(MyState.UnLock)
        assert(sm.dispatch(MyEvent.PressLock(withReturn = true))).isEqualTo(MyState.NotLoaned)
    }
}