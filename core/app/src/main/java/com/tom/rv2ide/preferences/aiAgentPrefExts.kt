/*
 *  This file is part of AndroidCodeStudio.
 *
 *  AndroidCodeStudio is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidCodeStudio is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidCodeStudio.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.tom.rv2ide.preferences

import android.content.Context
import androidx.preference.Preference
import com.tom.rv2ide.R
import com.tom.rv2ide.artificial.git.GithubAutoPush
import com.tom.rv2ide.artificial.secrets.ApiKey
import com.tom.rv2ide.preferences.internal.prefManager
import com.tom.rv2ide.resources.R.string
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/** * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null */
@Parcelize
class AIAgentPreferencesScreen(
    override val key: String = "idepref_ai_agent",
    override val title: Int = string.ai_agent_title,
    override val summary: Int? = string.ai_agent_description,
    override val children: List<IPreference> = mutableListOf(),
) : IPreferenceScreen() {

  init {
    val geminiApiKeyPref = GeminiApiKey()
    val deepseekApiKeyPref = DeepseekApiKey()
    val openAIApiKeyPref = OpenAIApiKey()
    val openAIBaseUrlPref = OpenAIBaseUrl()
    val anthropicApiKeyPref = AnthropicApiKey()
    val grokApiKeyPref = GrokApiKey()
    val autoPushEnabledPref = AutoPushEnabled()
    val autoPushRepoPref = AutoPushRepo()
    val autoPushBranchPref = AutoPushBranch()
    val autoPushPatPref = AutoPushPat()

    val toggleDependents = { isEnabled: Boolean ->
      geminiApiKeyPref.setEnabled(isEnabled)
      deepseekApiKeyPref.setEnabled(isEnabled)
      openAIApiKeyPref.setEnabled(isEnabled)
      openAIBaseUrlPref.setEnabled(isEnabled)
      anthropicApiKeyPref.setEnabled(isEnabled)
      grokApiKeyPref.setEnabled(isEnabled)
      autoPushEnabledPref.setEnabled(isEnabled)
      autoPushRepoPref.setEnabled(isEnabled)
      autoPushBranchPref.setEnabled(isEnabled)
      autoPushPatPref.setEnabled(isEnabled)
    }

    val aiAgentEnabled = AIAgentEnabled { isEnabled -> toggleDependents(isEnabled) }

    addPreference(
        GeneralConfig().apply {
          addPreference(aiAgentEnabled)
        })
    addPreference(
        ApiKeysConfig().apply {
          addPreference(geminiApiKeyPref)
          addPreference(deepseekApiKeyPref)
          addPreference(openAIApiKeyPref)
          addPreference(openAIBaseUrlPref)
          addPreference(anthropicApiKeyPref)
          addPreference(grokApiKeyPref)
        })
    addPreference(
        AutoPushConfig().apply {
          addPreference(autoPushEnabledPref)
          addPreference(autoPushRepoPref)
          addPreference(autoPushBranchPref)
          addPreference(autoPushPatPref)
        })
  }
}

@Parcelize
private class GeneralConfig(
    override val key: String = "idepref_ai_agent_general",
    override val title: Int = string.ai_agent_group_general,
    override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup()

@Parcelize
private class ApiKeysConfig(
    override val key: String = "idepref_ai_agent_api_keys",
    override val title: Int = string.ai_agent_group_api_keys,
    override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup()

@Parcelize
private class AutoPushConfig(
    override val key: String = "idepref_ai_agent_autopush",
    override val title: Int = string.ai_agent_group_autopush,
    override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup()


@Parcelize
private class AIAgentEnabled(
    override val key: String = "ai_agent_enabled",
    override val title: Int = R.string.ai_agent_enable,
    @IgnoredOnParcel private val onStateChanged: ((Boolean) -> Unit)? = null,
) :
    SwitchPreference(
        setValue = { isEnabled ->
          prefManager.putBoolean("ai_agent_enabled", isEnabled)
          onStateChanged?.invoke(isEnabled)
        },
        getValue = { prefManager.getBoolean("ai_agent_enabled", false) },
    ) {

  override fun onCreatePreference(context: Context): Preference {
    return super.onCreatePreference(context).apply {
      key = "ai_agent_enabled"
      title = context.getString(R.string.ai_agent_enable)
      summary = context.getString(R.string.ai_agent_enable_summary)
    }
  }
}


@Parcelize
private class GrokApiKey(
    override val key: String = "ai_agent_grok_api_key",
    override val title: Int = R.string.ai_agent_grok_api_key,
) : BasePreference() {

  @IgnoredOnParcel private var preference: Preference? = null

  override fun onCreatePreference(context: Context): Preference {
    preference =
        androidx.preference.Preference(context).apply {
          key = "ai_agent_grok_api_key"
          title = context.getString(R.string.ai_agent_grok_api_key)
          summary = getSummaryText()
          isEnabled = prefManager.getBoolean("ai_agent_enabled", false)
        }
    return preference!!
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    val context = preference.context

    val editText = android.widget.EditText(context)
    editText.setText(prefManager.getString("ai_agent_grok_api_key", ""))
    editText.hint = "Enter your xAI Grok API key"

    val dialog =
        com.google.android.material.dialog
            .MaterialAlertDialogBuilder(context)
            .setTitle("Grok API Key")
            .setMessage("Enter your xAI Grok API key")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
              val apiKey = editText.text.toString().trim()
              prefManager.putString("ai_agent_grok_api_key", apiKey)
              preference.summary = getSummaryText()
            }
            .setNegativeButton("Cancel", null)
            .create()

    dialog.show()
    return true
  }

  fun setEnabled(enabled: Boolean) {
    preference?.isEnabled = enabled
  }

  private fun getSummaryText(): String {
    val apiKey = prefManager.getString("ai_agent_grok_api_key", "")
    return if (apiKey.isBlank()) "Click to set API key" else "API Key: ${apiKey.take(8)}..."
  }
}

@Parcelize
private class GeminiApiKey(
    override val key: String = "ai_agent_gemini_api_key",
    override val title: Int = R.string.ai_agent_api_key,
) : BasePreference() {

  @IgnoredOnParcel private var preference: Preference? = null

  override fun onCreatePreference(context: Context): Preference {
    preference =
        androidx.preference.Preference(context).apply {
          key = "ai_agent_gemini_api_key"
          title = context.getString(R.string.ai_agent_api_key)
          summary = getSummaryText()
          isEnabled = prefManager.getBoolean("ai_agent_enabled", false)
        }
    return preference!!
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    val context = preference.context

    val editText = android.widget.EditText(context)
    editText.setText(prefManager.getString("ai_agent_gemini_api_key", ""))
    editText.hint = "Enter your Google Gemini API key"

    val dialog =
        com.google.android.material.dialog
            .MaterialAlertDialogBuilder(context)
            .setTitle("Gemini API Key")
            .setMessage("Enter your Google Gemini API key")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
              val apiKey = editText.text.toString().trim()
              prefManager.putString("ai_agent_gemini_api_key", apiKey)
              preference.summary = getSummaryText()
            }
            .setNegativeButton("Cancel", null)
            .create()

    dialog.show()
    return true
  }

  fun setEnabled(enabled: Boolean) {
    preference?.isEnabled = enabled
  }

  private fun getSummaryText(): String {
    val apiKey = prefManager.getString("ai_agent_gemini_api_key", "")
    return if (apiKey.isBlank()) "Click to set API key" else "API Key: ${apiKey.take(8)}..."
  }
}

@Parcelize
private class DeepseekApiKey(
    override val key: String = "ai_agent_deepseek_api_key",
    override val title: Int = R.string.ai_agent_deepseek_api_key,
) : BasePreference() {

  @IgnoredOnParcel private var preference: Preference? = null

  override fun onCreatePreference(context: Context): Preference {
    preference =
        androidx.preference.Preference(context).apply {
          key = "ai_agent_deepseek_api_key"
          title = context.getString(R.string.ai_agent_deepseek_api_key)
          summary = getSummaryText()
          isEnabled = prefManager.getBoolean("ai_agent_enabled", false)
        }
    return preference!!
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    val context = preference.context

    val editText = android.widget.EditText(context)
    editText.setText(prefManager.getString("ai_agent_deepseek_api_key", ""))
    editText.hint = "Enter your Deepseek API key"

    val dialog =
        com.google.android.material.dialog
            .MaterialAlertDialogBuilder(context)
            .setTitle("Deepseek API Key")
            .setMessage("Enter your Deepseek API key")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
              val apiKey = editText.text.toString().trim()
              prefManager.putString("ai_agent_deepseek_api_key", apiKey)
              preference.summary = getSummaryText()
            }
            .setNegativeButton("Cancel", null)
            .create()

    dialog.show()
    return true
  }

  fun setEnabled(enabled: Boolean) {
    preference?.isEnabled = enabled
  }

  private fun getSummaryText(): String {
    val apiKey = prefManager.getString("ai_agent_deepseek_api_key", "")
    return if (apiKey.isBlank()) "Click to set API key" else "API Key: ${apiKey.take(8)}..."
  }
}

@Parcelize
private class OpenAIApiKey(
    override val key: String = "ai_agent_openai_api_key",
    override val title: Int = R.string.ai_agent_openai_api_key,
) : BasePreference() {

  @IgnoredOnParcel private var preference: Preference? = null

  override fun onCreatePreference(context: Context): Preference {
    preference =
        androidx.preference.Preference(context).apply {
          key = "ai_agent_openai_api_key"
          title = context.getString(R.string.ai_agent_openai_api_key)
          summary = getSummaryText()
          isEnabled = prefManager.getBoolean("ai_agent_enabled", false)
        }
    return preference!!
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    val context = preference.context

    val editText = android.widget.EditText(context)
    editText.setText(prefManager.getString("ai_agent_openai_api_key", ""))
    editText.hint = "Enter your OpenAI API key"

    val dialog =
        com.google.android.material.dialog
            .MaterialAlertDialogBuilder(context)
            .setTitle("OpenAI API Key")
            .setMessage("Enter your OpenAI API key")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
              val apiKey = editText.text.toString().trim()
              prefManager.putString("ai_agent_openai_api_key", apiKey)
              preference.summary = getSummaryText()
            }
            .setNegativeButton("Cancel", null)
            .create()

    dialog.show()
    return true
  }

  fun setEnabled(enabled: Boolean) {
    preference?.isEnabled = enabled
  }

  private fun getSummaryText(): String {
    val apiKey = prefManager.getString("ai_agent_openai_api_key", "")
    return if (apiKey.isBlank()) "Click to set API key" else "API Key: ${apiKey.take(8)}..."
  }
}

@Parcelize
private class AnthropicApiKey(
    override val key: String = "ai_agent_anthropic_api_key",
    override val title: Int = R.string.ai_agent_anthropic_api_key,
) : BasePreference() {

  @IgnoredOnParcel private var preference: Preference? = null

  override fun onCreatePreference(context: Context): Preference {
    preference =
        androidx.preference.Preference(context).apply {
          key = "ai_agent_anthropic_api_key"
          title = context.getString(R.string.ai_agent_anthropic_api_key)
          summary = getSummaryText()
          isEnabled = prefManager.getBoolean("ai_agent_enabled", false)
        }
    return preference!!
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    val context = preference.context

    val editText = android.widget.EditText(context)
    editText.setText(prefManager.getString("ai_agent_anthropic_api_key", ""))
    editText.hint = "Enter your Anthropic API key"

    val dialog =
        com.google.android.material.dialog
            .MaterialAlertDialogBuilder(context)
            .setTitle("Anthropic API Key")
            .setMessage("Enter your Anthropic API key")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
              val apiKey = editText.text.toString().trim()
              prefManager.putString("ai_agent_anthropic_api_key", apiKey)
              preference.summary = getSummaryText()
            }
            .setNegativeButton("Cancel", null)
            .create()

    dialog.show()
    return true
  }

  fun setEnabled(enabled: Boolean) {
    preference?.isEnabled = enabled
  }

  private fun getSummaryText(): String {
    val apiKey = prefManager.getString("ai_agent_anthropic_api_key", "")
    return if (apiKey.isBlank()) "Click to set API key" else "API Key: ${apiKey.take(8)}..."
  }
}

@Parcelize
private class OpenAIBaseUrl(
    override val key: String = "ai_agent_openai_base_url",
    override val title: Int = R.string.ai_agent_openai_base_url,
) : BasePreference() {

  @IgnoredOnParcel private var preference: Preference? = null

  override fun onCreatePreference(context: Context): Preference {
    preference =
        androidx.preference.Preference(context).apply {
          key = "ai_agent_openai_base_url"
          title = context.getString(R.string.ai_agent_openai_base_url)
          summary = getSummaryText()
          isEnabled = prefManager.getBoolean("ai_agent_enabled", false)
        }
    return preference!!
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    val context = preference.context
    val editText = android.widget.EditText(context)
    editText.setText(ApiKey.getOpenAIBaseUrl())
    editText.hint = "https://your-openai-compatible-endpoint/v1"

    com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
        .setTitle("OpenAI-compatible Base URL")
        .setMessage("Leave as default to use the official OpenAI endpoint. Any OpenAI-compatible service (DeepSeek, Moonshot, Ollama, etc.) works here.")
        .setView(editText)
        .setPositiveButton("Save") { _, _ ->
          ApiKey.setOpenAIBaseUrl(editText.text.toString().trim())
          preference.summary = getSummaryText()
        }
        .setNegativeButton("Cancel", null)
        .create()
        .show()
    return true
  }

  fun setEnabled(enabled: Boolean) {
    preference?.isEnabled = enabled
  }

  private fun getSummaryText(): String {
    val url = ApiKey.getOpenAIBaseUrl()
    return if (url == "https://api.openai.com/v1") "Default (OpenAI)" else url
  }
}

@Parcelize
private class AutoPushEnabled(
    override val key: String = "ai_autopush_enabled",
    override val title: Int = R.string.ai_agent_auto_push,
) : SwitchPreference(
    setValue = { GithubAutoPush.setEnabled(it) },
    getValue = { GithubAutoPush.isEnabled() },
) {

  @IgnoredOnParcel private var preference: Preference? = null

  override fun onCreatePreference(context: Context): Preference {
    preference = super.onCreatePreference(context).apply {
      key = "ai_autopush_enabled"
      title = context.getString(R.string.ai_agent_auto_push)
      summary = context.getString(R.string.ai_agent_auto_push_summary)
      isEnabled = prefManager.getBoolean("ai_agent_enabled", false)
    }
    return preference!!
  }

  fun setEnabled(enabled: Boolean) {
    preference?.isEnabled = enabled
  }
}

@Parcelize
private class AutoPushRepo(
    override val key: String = "ai_autopush_repo",
    override val title: Int = R.string.ai_agent_auto_push_repo,
) : BasePreference() {

  @IgnoredOnParcel private var preference: Preference? = null

  override fun onCreatePreference(context: Context): Preference {
    preference =
        androidx.preference.Preference(context).apply {
          key = "ai_autopush_repo"
          title = context.getString(R.string.ai_agent_auto_push_repo)
          summary = getSummaryText()
          isEnabled = prefManager.getBoolean("ai_agent_enabled", false)
        }
    return preference!!
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    val context = preference.context
    val editText = android.widget.EditText(context)
    editText.setText(GithubAutoPush.getRepo())
    editText.hint = "your-github-username/your-repo"

    com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
        .setTitle("GitHub Repo (owner/name)")
        .setMessage("The repository to push AI changes to after a run succeeds.")
        .setView(editText)
        .setPositiveButton("Save") { _, _ ->
          GithubAutoPush.setRepo(editText.text.toString().trim())
          preference.summary = getSummaryText()
        }
        .setNegativeButton("Cancel", null)
        .create()
        .show()
    return true
  }

  fun setEnabled(enabled: Boolean) {
    preference?.isEnabled = enabled
  }

  private fun getSummaryText(): String {
    val repo = GithubAutoPush.getRepo()
    return if (repo.isBlank()) "Click to set GitHub repo" else "Repo: $repo"
  }
}

@Parcelize
private class AutoPushBranch(
    override val key: String = "ai_autopush_branch",
    override val title: Int = R.string.ai_agent_auto_push_branch,
) : BasePreference() {

  @IgnoredOnParcel private var preference: Preference? = null

  override fun onCreatePreference(context: Context): Preference {
    preference =
        androidx.preference.Preference(context).apply {
          key = "ai_autopush_branch"
          title = context.getString(R.string.ai_agent_auto_push_branch)
          summary = getSummaryText()
          isEnabled = prefManager.getBoolean("ai_agent_enabled", false)
        }
    return preference!!
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    val context = preference.context
    val editText = android.widget.EditText(context)
    editText.setText(GithubAutoPush.getBranch())
    editText.hint = "main"

    com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
        .setTitle("GitHub Branch")
        .setMessage("Branch to commit and push AI changes to.")
        .setView(editText)
        .setPositiveButton("Save") { _, _ ->
          GithubAutoPush.setBranch(editText.text.toString().trim())
          preference.summary = getSummaryText()
        }
        .setNegativeButton("Cancel", null)
        .create()
        .show()
    return true
  }

  fun setEnabled(enabled: Boolean) {
    preference?.isEnabled = enabled
  }

  private fun getSummaryText(): String {
    val branch = GithubAutoPush.getBranch()
    return if (branch.isBlank()) "Click to set branch" else "Branch: $branch"
  }
}

@Parcelize
private class AutoPushPat(
    override val key: String = "ai_autopush_pat",
    override val title: Int = R.string.ai_agent_auto_push_pat,
) : BasePreference() {

  @IgnoredOnParcel private var preference: Preference? = null

  override fun onCreatePreference(context: Context): Preference {
    preference =
        androidx.preference.Preference(context).apply {
          key = "ai_autopush_pat"
          title = context.getString(R.string.ai_agent_auto_push_pat)
          summary = getSummaryText()
          isEnabled = prefManager.getBoolean("ai_agent_enabled", false)
        }
    return preference!!
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    val context = preference.context
    val editText = android.widget.EditText(context)
    editText.setText(GithubAutoPush.getPat())
    editText.hint = "ghp_..."

    com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
        .setTitle("GitHub Personal Access Token")
        .setMessage("A GitHub PAT with repo scope so AI changes can be pushed automatically.")
        .setView(editText)
        .setPositiveButton("Save") { _, _ ->
          GithubAutoPush.setPat(editText.text.toString().trim())
          preference.summary = getSummaryText()
        }
        .setNegativeButton("Cancel", null)
        .create()
        .show()
    return true
  }

  fun setEnabled(enabled: Boolean) {
    preference?.isEnabled = enabled
  }

  private fun getSummaryText(): String {
    val pat = GithubAutoPush.getPat()
    return if (pat.isBlank()) "Click to set PAT" else "PAT: ${pat.take(8)}..."
  }
}
