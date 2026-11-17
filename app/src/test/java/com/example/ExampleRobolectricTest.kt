package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name and firestore database id from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Alif Car Import", context.getString(R.string.app_name))
        assertEquals(
            "ai-studio-alifcarportal-c5b73261-7322-4966-b5e2-59c51963e99b",
            context.getString(R.string.firestore_database_id)
        )
    }
}
