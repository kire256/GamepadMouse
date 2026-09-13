package com.droidforge.gamepadmouse.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.profileStore: DataStore<Preferences> by preferencesDataStore(name = "device_profiles")

/**
 * Manages per-device profiles - storing and loading settings for different gamepads.
 */
class ProfileManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ProfileManager"
        private const val DEFAULT_DEVICE_ID = "default"
        private const val ACTIVE_DEVICE_KEY = "active_device_id"
    }
    
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
    
    private val activeDeviceIdKey = stringPreferencesKey(ACTIVE_DEVICE_KEY)
    
    /**
     * Get the currently active device ID
     */
    val activeDeviceId: Flow<String> = context.profileStore.data.map { prefs ->
        prefs[activeDeviceIdKey] ?: DEFAULT_DEVICE_ID
    }
    
    /**
     * Get all saved profiles
     */
    suspend fun getAllProfiles(): List<DeviceProfile> {
        val prefs = context.profileStore.data.first()
        return prefs.asMap().entries
            .filter { it.key.name != ACTIVE_DEVICE_KEY }
            .mapNotNull { (key, value) ->
                try {
                    json.decodeFromString<DeviceProfile>(value as String)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decode profile ${key.name}: ${e.message}")
                    null
                }
            }
            .sortedByDescending { it.lastUsed }
    }
    
    /**
     * Get a specific profile by device ID
     */
    suspend fun getProfile(deviceId: String): DeviceProfile? {
        val key = stringPreferencesKey(deviceId)
        val prefs = context.profileStore.data.first()
        val jsonStr = prefs[key] ?: return null
        return try {
            json.decodeFromString<DeviceProfile>(jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode profile $deviceId", e)
            null
        }
    }
    
    /**
     * Save a profile for a device
     */
    suspend fun saveProfile(profile: DeviceProfile) {
        val key = stringPreferencesKey(profile.deviceId)
        val jsonStr = json.encodeToString(profile)
        context.profileStore.edit { prefs ->
            prefs[key] = jsonStr
        }
        Log.i(TAG, "Saved profile for ${profile.deviceName} (${profile.deviceId})")
    }
    
    /**
     * Delete a profile
     */
    suspend fun deleteProfile(deviceId: String) {
        if (deviceId == DEFAULT_DEVICE_ID) {
            Log.w(TAG, "Cannot delete default profile")
            return
        }
        val key = stringPreferencesKey(deviceId)
        context.profileStore.edit { prefs ->
            prefs.remove(key)
        }
        Log.i(TAG, "Deleted profile $deviceId")
    }
    
    /**
     * Set the active device ID (which profile to use)
     */
    suspend fun setActiveDevice(deviceId: String) {
        context.profileStore.edit { prefs ->
            prefs[activeDeviceIdKey] = deviceId
        }
        Log.i(TAG, "Active device set to $deviceId")
    }
    
    /**
     * Get or create a profile for a device
     */
    suspend fun getOrCreateProfile(
        deviceId: String,
        deviceName: String,
        defaultSettings: ProfileSettings
    ): DeviceProfile {
        return getProfile(deviceId) ?: run {
            val newProfile = DeviceProfile(
                deviceId = deviceId,
                deviceName = deviceName,
                settings = defaultSettings,
                lastUsed = System.currentTimeMillis()
            )
            saveProfile(newProfile)
            newProfile
        }
    }
    
    /**
     * Update the last-used timestamp for a profile
     */
    suspend fun touchProfile(deviceId: String) {
        val profile = getProfile(deviceId) ?: return
        val updated = profile.copy(lastUsed = System.currentTimeMillis())
        saveProfile(updated)
    }
    
    /**
     * Rename a device profile
     */
    suspend fun renameDevice(deviceId: String, newName: String) {
        val profile = getProfile(deviceId) ?: return
        val updated = profile.copy(deviceName = newName)
        saveProfile(updated)
    }
}
