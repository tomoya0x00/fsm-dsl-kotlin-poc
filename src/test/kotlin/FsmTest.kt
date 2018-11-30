import assertk.assert
import assertk.assertions.isEqualTo
import org.junit.Test

class FsmTest {

    enum class MyState : BaseState {
        NOT_LOANED,
        ON_LOAN,
        LOCK,
        UNLOCK
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

        val sm = stateMachine(initial = MyState.NOT_LOANED) {
            state(MyState.NOT_LOANED,
                    entry = { history.add("in_NotLoaned") },
                    exit = { history.add("out_NotLoaned") }
            ) {
                edge(MyEvent.PressRental::class, next = MyState.LOCK)
            }
            state(MyState.ON_LOAN,
                    entry = { history.add("in_OnLoan") },
                    exit = { history.add("out_OnLoan") }
            ) {
                state(MyState.LOCK,
                        entry = { history.add("in_Lock") },
                        exit = { history.add("out_Lock") }
                ) {
                    edge(MyEvent.PressReturn::class, next = MyState.NOT_LOANED)
                    edge(MyEvent.PressUnLock::class, next = MyState.UNLOCK)
                }
                state(MyState.UNLOCK,
                        entry = { history.add("in_UnLock") },
                        exit = { history.add("out_UnLock") }
                ) {
                    edge(MyEvent.PressLock::class, guard = { !it.withReturn }, next = MyState.LOCK)
                    edge(MyEvent.PressLock::class, guard = { it.withReturn }, next = MyState.NOT_LOANED) {
                        history.add("action_PressLockWithReturn")
                    }
                }
            }
        }

        assert(history).isEqualTo(listOf(
                "in_NotLoaned"
        ))

        history.clear()
        assert(sm.dispatch(MyEvent.PressRental)).isEqualTo(MyState.LOCK)
        assert(history).isEqualTo(listOf(
                "out_NotLoaned",
                "in_OnLoan",
                "in_Lock"
        ))

        history.clear()
        assert(sm.dispatch(MyEvent.PressUnLock)).isEqualTo(MyState.UNLOCK)
        assert(history).isEqualTo(listOf(
                "out_Lock",
                "in_UnLock"
        ))

        history.clear()
        assert(sm.dispatch(MyEvent.PressLock(withReturn = false))).isEqualTo(MyState.LOCK)
        assert(history).isEqualTo(listOf(
                "out_UnLock",
                "in_Lock"
        ))

        history.clear()
        assert(sm.dispatch(MyEvent.PressUnLock)).isEqualTo(MyState.UNLOCK)
        assert(history).isEqualTo(listOf(
                "out_Lock",
                "in_UnLock"
        ))

        history.clear()
        assert(sm.dispatch(MyEvent.PressLock(withReturn = true))).isEqualTo(MyState.NOT_LOANED)
        assert(history).isEqualTo(listOf(
                "action_PressLockWithReturn",
                "out_UnLock",
                "out_OnLoan",
                "in_NotLoaned"
        ))

    }
}