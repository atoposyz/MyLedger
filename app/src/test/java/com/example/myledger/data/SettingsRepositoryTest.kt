package com.example.myledger.data

import android.app.Application
import androidx.datastore.preferences.core.*
import com.example.myledger.data.settings.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class SettingsRepositoryTest {
    private val jobs = mutableListOf<Job>()
    private val file = File(RuntimeEnvironment.getApplication().cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
    private fun store(): androidx.datastore.core.DataStore<Preferences> {
        val job = SupervisorJob().also(jobs::add)
        return PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }
    }
    @After fun close() = runBlocking { jobs.forEach { it.cancelAndJoin() } }
    @Test fun defaultsToSystemAndFixedCny() = runBlocking {
        val settings = SettingsRepository(store()).current()
        assertEquals(ThemeMode.SYSTEM, settings.themeMode); assertEquals("CNY", settings.currency)
    }
    @Test fun themeIsPersistedOnDiskAndSurvivesNewStoreInstance() = runBlocking {
        val repository = SettingsRepository(store()); repository.setTheme(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repository.current().themeMode)
        jobs.last().cancelAndJoin()
        val reopened = SettingsRepository(store()); assertEquals(ThemeMode.DARK, reopened.current().themeMode)
        reopened.setTheme(ThemeMode.LIGHT); assertEquals(ThemeMode.LIGHT, reopened.current().themeMode)
    }
    @Test fun unknownSavedThemeFallsBackToSystemWithoutChangingCurrency() = runBlocking {
        val store = store(); store.edit { it[stringPreferencesKey("theme_mode")] = "unknown" }
        assertEquals(AppSettings(), SettingsRepository(store).current())
    }
}
