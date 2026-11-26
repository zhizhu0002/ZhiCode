package com.zhizhu.zhicode.compose.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OobePermissionsTest {

    @Test
    fun allRequiredGranted_requiresStorageNotificationsAndBattery() {
        val missingAllFiles = OobePermissionsSnapshot(
            legacyStorageGranted = true,
            allFilesGranted = false,
            notificationsGranted = true,
            batteryOptimizationIgnored = true,
        )
        assertFalse(missingAllFiles.storageGranted)
        assertFalse(missingAllFiles.allRequiredGranted)

        val missingLegacyStorage = OobePermissionsSnapshot(
            legacyStorageGranted = false,
            allFilesGranted = true,
            notificationsGranted = true,
            batteryOptimizationIgnored = true,
        )
        assertFalse(missingLegacyStorage.storageGranted)
        assertFalse(missingLegacyStorage.allRequiredGranted)

        val missingNotifications = OobePermissionsSnapshot(
            legacyStorageGranted = true,
            allFilesGranted = true,
            notificationsGranted = false,
            batteryOptimizationIgnored = true,
        )
        assertFalse(missingNotifications.allRequiredGranted)

        val missingBattery = OobePermissionsSnapshot(
            legacyStorageGranted = true,
            allFilesGranted = true,
            notificationsGranted = true,
            batteryOptimizationIgnored = false,
        )
        assertFalse(missingBattery.allRequiredGranted)

        val readyWithoutOptionals = OobePermissionsSnapshot(
            legacyStorageGranted = true,
            allFilesGranted = true,
            notificationsGranted = true,
            batteryOptimizationIgnored = true,
            installPackagesGranted = false,
            rootBinaryPresent = false,
        )
        assertTrue(readyWithoutOptionals.storageGranted)
        assertTrue(readyWithoutOptionals.allRequiredGranted)
    }

    @Test
    fun oobeSteps_keepThreeStepWizardOrder() {
        assertEquals(
            listOf(OobeStep.PERMISSIONS, OobeStep.RUNTIME, OobeStep.MODEL),
            OobeStep.entries,
        )
        assertEquals(listOf(0, 1, 2), OobeStep.entries.map { it.index })
    }
}
