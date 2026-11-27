package com.zhizhu.zhicode.pi

import com.termux.app.zhicode.model.ToolCall
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class PiHostToolCalls {
    private val calls = mutableMapOf<String, Request>()
    private var active = false

    @Synchronized
    fun beginTurn() {
        endTurn()
        active = true
    }

    @Synchronized
    fun register(call: ToolCall) {
        if (active && call.id.isNotBlank() && call.id !in calls) calls[call.id] = Request(call)
    }

    @Synchronized
    fun claim(id: String, tool: String): Request? {
        val request = calls[id] ?: return null
        if (request.call.name != tool || !request.claim()) return null
        return request
    }

    @Synchronized
    fun finish(id: String) {
        calls[id]?.cancel()
    }

    @Synchronized
    fun endTurn(): Boolean {
        val wasActive = active
        active = false
        calls.values.forEach(Request::cancel)
        calls.clear()
        return wasActive
    }

    @Synchronized
    fun respond(id: String, allow: Boolean): Boolean = calls.values.any { it.respond(id, allow) }

    @Synchronized
    fun isAwaitingApproval(id: String): Boolean = calls.values.any { it.isAwaitingApproval(id) }

    internal class Request(val call: ToolCall) {
        val permissionId = "pi-host-${UUID.randomUUID()}"
        private val decision = CompletableFuture<Boolean>()
        private var claimed = false
        private var awaitingApproval = false
        private var started = false
        private var cancelled = false
        private var worker: Future<*>? = null

        @Synchronized
        fun claim(): Boolean {
            if (claimed || cancelled) return false
            claimed = true
            return true
        }

        @Synchronized
        fun submit(executor: ExecutorService, action: () -> Unit): Boolean {
            if (cancelled) return false
            worker = executor.submit(action)
            return true
        }

        fun awaitApproval(show: (String) -> Unit): Boolean {
            synchronized(this) {
                if (cancelled) return false
                awaitingApproval = true
            }
            try {
                show(permissionId)
                return decision.get(30, TimeUnit.MINUTES)
            } catch (_: TimeoutException) {
                decision.complete(false)
                return false
            } finally {
                synchronized(this) { awaitingApproval = false }
            }
        }

        @Synchronized
        fun respond(id: String, allow: Boolean): Boolean =
            isAwaitingApproval(id) && decision.complete(allow)

        @Synchronized
        fun isAwaitingApproval(id: String): Boolean =
            !cancelled && awaitingApproval && id == permissionId && !decision.isDone

        @Synchronized
        fun beginExecution(): Boolean {
        if (!claimed || cancelled || awaitingApproval || started || !decision.getNow(true) ||
            Thread.currentThread().isInterrupted
        ) return false
            started = true
            return true
        }

        @Synchronized
        fun isActive(): Boolean = !cancelled

        @Synchronized
        fun cancel() {
            cancelled = true
            decision.complete(false)
            worker?.cancel(true)
        }
    }
}
