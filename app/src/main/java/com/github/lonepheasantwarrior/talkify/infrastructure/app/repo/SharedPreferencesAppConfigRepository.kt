package com.github.lonepheasantwarrior.talkify.infrastructure.app.repo

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.lonepheasantwarrior.talkify.domain.repository.AppConfigRepository

/**
 * 基于 SharedPreferences 的应用配置仓储实现
 *
 * 存储应用级全局配置，如用户选择的供应商 ID
 * 与供应商特定配置（apiKey、voiceId）分离
 * 不与任何特定 TTS 供应商绑定
 */
class SharedPreferencesAppConfigRepository(
    context: Context
) : AppConfigRepository {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // 一次性迁移：将旧版本使用的 "selected_engine" 键迁移至新的 "selected_provider" 键，
        // 避免已安装用户在概念更名后丢失已选择的供应商。
        migrateLegacySelectedProviderKey()
    }

    private fun migrateLegacySelectedProviderKey() {
        if (!sharedPreferences.contains(KEY_SELECTED_PROVIDER) &&
            sharedPreferences.contains(KEY_SELECTED_PROVIDER_LEGACY)
        ) {
            val legacyValue = sharedPreferences.getString(KEY_SELECTED_PROVIDER_LEGACY, null)
            if (legacyValue != null) {
                sharedPreferences.edit {
                    putString(KEY_SELECTED_PROVIDER, legacyValue)
                    remove(KEY_SELECTED_PROVIDER_LEGACY)
                }
            }
        }
    }

    override fun getSelectedProviderId(): String? {
        return sharedPreferences.getString(KEY_SELECTED_PROVIDER, null)
    }

    override fun saveSelectedProviderId(providerId: String) {
        sharedPreferences.edit {
            putString(KEY_SELECTED_PROVIDER, providerId)
        }
    }

    override fun hasSelectedProvider(): Boolean {
        return sharedPreferences.contains(KEY_SELECTED_PROVIDER)
    }

    override fun hasRequestedNotificationPermission(): Boolean {
        return sharedPreferences.getBoolean(KEY_HAS_REQUESTED_NOTIFICATION, false)
    }

    override fun setHasRequestedNotificationPermission(requested: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_HAS_REQUESTED_NOTIFICATION, requested)
        }
    }

    override fun hasOpenedAboutPage(): Boolean {
        return sharedPreferences.getBoolean(KEY_HAS_OPENED_ABOUT_PAGE, false)
    }

    override fun setAboutPageOpened(opened: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_HAS_OPENED_ABOUT_PAGE, opened)
        }
    }

    companion object {
        private const val PREFS_NAME = "talkify_app_config"
        private const val KEY_SELECTED_PROVIDER = "selected_provider"
        private const val KEY_SELECTED_PROVIDER_LEGACY = "selected_engine"
        private const val KEY_HAS_REQUESTED_NOTIFICATION = "has_requested_notification"
        private const val KEY_HAS_OPENED_ABOUT_PAGE = "has_opened_about_page"
    }
}
