package com.raisethebar.app

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Milestone 14 security checks on the merged manifest. */
@RunWith(AndroidJUnit4::class)
class ManifestTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun info(flags: Int) = context.packageManager.getPackageInfo(context.packageName, flags)

    @Test
    fun theAppCannotUseTheNetwork() {
        val asked = info(PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        assertFalse(Manifest.permission.INTERNET in asked)
        assertFalse(Manifest.permission.ACCESS_NETWORK_STATE in asked)
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC)
    }

    @Test
    fun healthDataStaysOutOfDeviceBackups() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun nothingOfOursIsExportedExceptTheLauncher() {
        val p = info(PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS)

        fun ours(name: String) = name.startsWith("com.raisethebar.")
        assertEquals(listOf("com.raisethebar.app.MainActivity"), p.activities.orEmpty().filter { it.exported && ours(it.name) }.map { it.name })
        assertTrue(p.receivers.orEmpty().none { it.exported && ours(it.name) })
        // Library receivers that are exported (the profile installer) only accept senders holding a system permission.
        assertTrue(p.receivers.orEmpty().filter { it.exported }.all { it.permission != null })
        assertTrue(p.providers.orEmpty().none { it.exported })
    }
}
