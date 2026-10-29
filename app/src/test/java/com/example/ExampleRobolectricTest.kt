package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.network.NetworkStatsHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun readStringFromContext() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("NetTrack", appName)
  }

  @Test
  fun testFormatBytes() {
    assertEquals("0 B", NetworkStatsHelper.formatBytes(0))
    assertTrue(NetworkStatsHelper.formatBytes(1024 * 1024).contains("MB"))
    assertTrue(NetworkStatsHelper.formatBytes(1024L * 1024L * 1024L * 2L).contains("GB"))
  }
}
