package com.zhizhu.zhicode.bridge

import com.termux.app.zhicode.model.PlanWorkflowState
import com.termux.app.zhicode.model.SessionConfig
import com.termux.app.zhicode.model.ToolExecutionResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidHostBridgeTest {
    private val config = SessionConfig().apply { shizukuExecutionEnabled = true }
    private val command = JSONObject().put("command", "id -u")

    @Test
    fun deviceCommandUsesTheCurrentPermissionMode() {
        assertTrue(AndroidHostBridge.requiresConfirmation(config, "Shizuku", command))
        assertNull(AndroidHostBridge.denialReason(config, "Shizuku", command))

        config.permissionMode = "bypassPermissions"
        assertEquals(false, AndroidHostBridge.requiresConfirmation(config, "Shizuku", command))

        config.planWorkflowState = PlanWorkflowState.planning(config.permissionMode)
        assertNotNull(AndroidHostBridge.denialReason(config, "Shizuku", command))
        assertNull(AndroidHostBridge.denialReason(config, "Sandbox", JSONObject().put("action", "status")))
    }

    @Test
    fun toolTogglesAndAllowlistApplyEvenInBypassMode() {
        config.permissionMode = "bypassPermissions"
        config.shizukuExecutionEnabled = false
        config.webSearchEnabled = false
        assertNotNull(AndroidHostBridge.denialReason(config, "Shizuku", command))
        assertNotNull(AndroidHostBridge.denialReason(config, "Root", command))
        assertNotNull(AndroidHostBridge.denialReason(config, "WebFetch", JSONObject()))
        assertNotNull(AndroidHostBridge.denialReason(config, "bash", command))
        assertNull(AndroidHostBridge.denialReason(config, "AndroidIntent", JSONObject()))
    }

    @Test
    fun dispatchUsesApprovedInputAndRechecksPolicyAfterThePrompt() {
        var executions = 0
        val bridge = AndroidHostBridge { _, tool, input ->
            assertEquals("Shizuku", tool)
            assertEquals("id -u", input.getString("command"))
            executions++
            ToolExecutionResult.ok("2000")
        }

        val declined = bridge.invoke("Shizuku", command, { config }, { _, _ -> false }, { true })
        assertTrue(declined.getBoolean("isError"))
        val cancelled = bridge.invoke("Shizuku", command, { config }, { _, _ -> true }, { false })
        assertTrue(cancelled.getBoolean("isError"))
        assertEquals(0, executions)

        val approved = bridge.invoke("Shizuku", command, { config }, { summary, _ ->
            assertEquals("id -u", summary)
            true
        }, { true })
        assertEquals("2000", approved.getString("text"))

        val revoked = bridge.invoke("Shizuku", command, { config }, { _, _ ->
            config.shizukuExecutionEnabled = false
            true
        }, { true })
        assertTrue(revoked.getBoolean("isError"))
        assertEquals(1, executions)
    }
}
