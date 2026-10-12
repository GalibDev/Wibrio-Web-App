package com.wibrio.app

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wibrio.mesh.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

private val Context.preferences by preferencesDataStore("wibrio_profile")
data class Profile(val name: String = "", val dark: Boolean = false, val ready: Boolean = false)
class ProfileStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val name = stringPreferencesKey("display_name")
    private val dark = booleanPreferencesKey("dark_theme")
    val profile = context.preferences.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { Profile(it[name].orEmpty(), it[dark] ?: false, true) }
    suspend fun saveName(value: String) { context.preferences.edit { it[name] = value.trim().take(40) } }
    suspend fun dark(value: Boolean) { context.preferences.edit { it[dark] = value } }
}
// Explicit unavailable implementation: Phase 1 must never invent wireless connectivity.
class FoundationMesh @Inject constructor() : MeshRepository {
    private val mutable = MutableStateFlow<MeshStatus>(MeshStatus.Inactive)
    override val status = mutable.asStateFlow()
    override suspend fun start(preference: TransportPreference) {
        mutable.value = MeshStatus.Unavailable("Nearby Connections arrives in Phase 2. No wireless session has started.")
    }
    override suspend fun stop() { mutable.value = MeshStatus.Inactive }
}
@HiltViewModel class WibrioViewModel @Inject constructor(private val store: ProfileStore, private val mesh: FoundationMesh) : ViewModel() {
    val profile = store.profile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Profile())
    val meshStatus = mesh.status
    val preference = MutableStateFlow(TransportPreference.AUTOMATIC)
    val error = MutableStateFlow<String?>(null)
    fun saveName(value: String) = viewModelScope.launch { runCatching { store.saveName(value) }.onFailure { error.value = "Could not save profile. Try again." } }
    fun dark(value: Boolean) = viewModelScope.launch { runCatching { store.dark(value) }.onFailure { error.value = "Could not save theme." } }
    fun start() = viewModelScope.launch { mesh.start(preference.value) }
    fun stop() = viewModelScope.launch { mesh.stop() }
}
