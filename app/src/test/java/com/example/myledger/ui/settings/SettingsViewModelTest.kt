package com.example.myledger.ui.settings

import android.app.Application
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.*
import com.example.myledger.data.settings.*
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class SettingsViewModelTest {
    private val job = SupervisorJob()
    private val stores = mutableListOf<ViewModelStore>()
    @After fun close() = runBlocking { stores.forEach { it.clear() }; job.cancelAndJoin() }
    private fun create(repository: SettingsRepository): SettingsViewModel {
        val store = ViewModelStore().also(stores::add)
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST") return SettingsViewModel(repository) as T
            }
        }
        return ViewModelProvider.create(store, factory)[SettingsViewModel::class.java].also { vm -> await { !vm.state.value.isLoading } }
    }
    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!predicate()) { shadowOf(Looper.getMainLooper()).idle(); if (System.nanoTime() > deadline) fail("Settings timed out"); Thread.sleep(10) }
    }
    @Test fun changesAndExternalUpdatesRefreshBothObservers() {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "vm-${UUID.randomUUID()}.preferences_pb")
        val repository = SettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file })
        val first = create(repository); val second = create(repository)
        first.setTheme(ThemeMode.DARK)
        await { !first.state.value.isSaving && second.state.value.settings.themeMode == ThemeMode.DARK }
        runBlocking { repository.setTheme(ThemeMode.LIGHT) }
        await { first.state.value.settings.themeMode == ThemeMode.LIGHT && second.state.value.settings.themeMode == ThemeMode.LIGHT }
    }
    @Test fun failedReadsCanRetryAndFailedWritesKeepPreviousTheme() {
        var readFailed = true
        val preferences = MutableStateFlow(emptyPreferences())
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> get() = flow { if (readFailed) throw IOException("read failure"); emitAll(preferences) }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw IOException("write failure")
        }
        val vm = create(SettingsRepository(store)); assertTrue(vm.state.value.readFailed)
        readFailed = false; vm.reload(); await { !vm.state.value.isLoading && !vm.state.value.readFailed }
        vm.setTheme(ThemeMode.DARK); await { !vm.state.value.isSaving && vm.state.value.saveFailed }
        assertEquals(ThemeMode.SYSTEM, vm.state.value.settings.themeMode)
    }
}
