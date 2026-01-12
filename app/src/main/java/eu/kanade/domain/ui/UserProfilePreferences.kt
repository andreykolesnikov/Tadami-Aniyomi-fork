package eu.kanade.domain.ui

import tachiyomi.core.common.preference.PreferenceStore

class UserProfilePreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun name() = preferenceStore.getString("user_profile_name", "Guest")
    fun avatarUrl() = preferenceStore.getString("user_profile_avatar_url", "")
    fun lastOpenedTime() = preferenceStore.getLong("user_profile_last_opened", 0L)
}
