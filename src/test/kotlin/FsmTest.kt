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
        object PressLock : MyEvent()
        object PressUnLock : MyEvent()
    }

    @Test
    fun test() {
        val sm = stateMachine(initial = MyState.NotLoaned) {
            state(MyState.NotLoaned) {
                edge(MyEvent.PressRental, next = MyState.Lock)
            }
            state(MyState.OnLoan,
                    entry = { println("turnOnRentalLed") },
                    exit = { println("turnOffRentalLed") }) {
                state(MyState.Lock) {
                    edge(MyEvent.PressReturn, next = MyState.NotLoaned)
                    edge(MyEvent.PressUnLock, next = MyState.UnLock)
                }
                state(MyState.UnLock) {
                    edge(MyEvent.PressLock, next = MyState.Lock)
                }
            }
        }

        val next = sm.dispatch(MyEvent.PressRental)
        assert(next).isEqualTo(MyState.Lock)
    }
}