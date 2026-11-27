package com.zhizhu.zhicode.pi

import com.termux.app.zhicode.model.ToolCall
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PiHostToolCallsTest {
    private val calls = PiHostToolCalls()
    private val call = ToolCall("tool-1", "Shizuku", JSONObject().put("command", "id -u"))

    @Test
    fun claimRequiresAnActiveMatchingCallAndSucceedsOnce() {
        calls.register(call)
        assertNull(calls.claim(call.id, call.name))
        calls.beginTurn()
        calls.register(call)
        assertNull(calls.claim(call.id, "Root"))
        assertSame(call, calls.claim(call.id, call.name)?.call)
        assertNull(calls.claim(call.id, call.name))
        calls.finish(call.id)
        calls.register(call)
        assertNull(calls.claim(call.id, call.name))
    }

    @Test
    fun approvalAppliesOnceToItsOwnRequest() {
        calls.beginTurn()
        calls.register(call)
        val request = checkNotNull(calls.claim(call.id, call.name))
        val first = request.awaitApproval { id ->
            assertFalse(calls.respond("$id-stale", true))
            assertTrue(calls.respond(id, true))
            assertFalse(calls.respond(id, false))
        }
        assertTrue(first)
        assertTrue(request.beginExecution())
        assertFalse(request.beginExecution())
    }

    @Test
    fun declineAndCancellationPreventDispatchAndRejectLateApproval() {
        calls.beginTurn()
        calls.register(call)
        val declined = checkNotNull(calls.claim(call.id, call.name))
        assertFalse(declined.awaitApproval { id -> assertTrue(calls.respond(id, false)) })
        assertFalse(declined.beginExecution())

        calls.beginTurn()
        calls.register(call)
        val cancelled = checkNotNull(calls.claim(call.id, call.name))
        var staleId = ""
        assertFalse(cancelled.awaitApproval { id ->
            staleId = id
            calls.endTurn()
            assertFalse(calls.respond(id, true))
        })
        assertFalse(cancelled.beginExecution())
        calls.beginTurn()
        calls.register(call)
        assertNotNull(calls.claim(call.id, call.name))
        assertFalse(calls.respond(staleId, true))
    }
}
