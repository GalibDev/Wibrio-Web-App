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
class ProfileStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val name = stringPreferencesKey("display_name")
    private val dark = booleanPreferencesKey("dark_theme")
    val profile = context.preferences.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { Profile(it[name].orEmpty(), it[dark] ?: false, true) }
    suspend fun saveName(value: String) { context.preferences.edit { it[name] = value.trim().take(40) } }
    suspend fun dark(value: Boolean) { context.preferences.edit { it[dark] = value } }
}
@HiltViewModel class WibrioViewModel @Inject constructor(private val store: ProfileStore, private val mesh: DirectMeshRepository) : ViewModel() {
    val profile = store.profile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Profile())
    val meshStatus = mesh.status
    val nearby = mesh.nearby
    val verification = mesh.verification
    val messages = mesh.messages
    val stats = mesh.stats
    val localId = mesh.localId
    val searching = mesh.searching
    val selectedPeer = MutableStateFlow<String?>(null)
    fun connect(endpoint: String) { mesh.connect(endpoint) }
    fun confirm(endpoint: String, accepted: Boolean) { mesh.confirm(endpoint, accepted) }
    fun disconnect(node: String) { mesh.disconnectPeer(node) }
    fun send(text: String) { selectedPeer.value?.let { mesh.send(it, text) } }
    override fun onCleared() { mesh.shutdown(); super.onCleared() }
    val preference = MutableStateFlow(TransportPreference.AUTOMATIC)
    val error = mesh.error
    fun saveName(value: String, onSaved: () -> Unit) = viewModelScope.launch {
        if (value.isBlank()) return@launch
        runCatching { store.saveName(value) }
            .onSuccess { onSaved() }
            .onFailure { error.value = "Could not save profile. Try again." }
    }
    fun dark(value: Boolean) = viewModelScope.launch { runCatching { store.dark(value) }.onFailure { error.value = "Could not save theme." } }
    fun start() = viewModelScope.launch { mesh.configureName(profile.value.name); mesh.start(preference.value) }
    fun stop() = viewModelScope.launch { mesh.stop() }
}
