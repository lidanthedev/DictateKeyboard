/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.dictate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.provider.ProviderAccount
import dev.patrickgold.florisboard.dictate.provider.ProviderPreset
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.dictate.provider.realtimeAccount
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiScope
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.florisDialogScroll
import org.florisboard.lib.compose.stringRes

/** The preset behind a keyring account, as DictateController resolves it. */
private fun presetOf(account: ProviderAccount): ProviderPreset? = when {
    account.isCustom -> ProviderRegistry.custom(account.customBaseUrl, realtime = account.customRealtime)
    else -> ProviderRegistry.byId(account.providerId)
}

private fun nameOf(account: ProviderAccount): String =
    account.displayName.ifBlank { presetOf(account)?.displayName ?: account.providerId }

/**
 * Accounts that can stream: a cloud preset with realtime support and a key (a custom server needs none),
 * or the on-device provider once a streaming model has been picked for it. Same tests as the controller.
 */
private fun canStream(account: ProviderAccount): Boolean {
    if (account.providerId == ProviderRegistry.LOCAL.id) return account.realtimeModel.isNotBlank()
    val preset = presetOf(account) ?: return false
    return preset.supportsRealtime && (account.hasKey || preset.isCustom)
}

/**
 * The real-time provider and model rows (shown under the real-time switch while streaming is on). The
 * provider is a pointer into the keyring — no second key is ever asked for — and the model is written to
 * that account's own `realtimeModel`, the slot the provider editor's "Real-time model" field also edits,
 * so the batch model is never touched.
 */
@Composable
internal fun PreferenceUiScope<FlorisPreferenceModel>.RealtimeProviderPreferences() {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val realtimeOn by prefs.dictate.realtimeTranscription.collectAsState()
    if (!realtimeOn) return
    val accounts by prefs.dictate.providerAccounts.collectAsState()
    val pickedId by prefs.dictate.realtimeProviderId.collectAsState()
    val transcriptionId by prefs.dictate.transcriptionProviderId.collectAsState()
    val transcription = accounts.getOrEmpty(transcriptionId)
    val active = accounts.realtimeAccount(pickedId, transcriptionId)
    // A pick that was removed from the keyring reads as "follow", exactly as the controller treats it.
    val following = pickedId.isBlank() || accounts[pickedId] == null
    val candidates = accounts.accounts.values.filter(::canStream).sortedBy { nameOf(it).lowercase() }

    var providerDialog by remember { mutableStateOf(false) }
    var modelDialog by remember { mutableStateOf(false) }

    val followLabel = stringRes(R.string.dictate__realtime_provider_follow, "name" to nameOf(transcription))
    Preference(
        icon = Icons.Default.Cloud,
        title = stringRes(R.string.dictate__realtime_provider_title),
        summary = when {
            !canStream(active) -> stringRes(R.string.dictate__realtime_provider_unsupported, "name" to nameOf(active))
            following -> followLabel
            else -> nameOf(active)
        },
        onClick = { providerDialog = true },
    )

    val activePreset = presetOf(active)
    // On-device streaming models are chosen in the on-device section, which also downloads them.
    if (canStream(active) && active.providerId != ProviderRegistry.LOCAL.id && activePreset != null) {
        val default = activePreset.defaultRealtimeModel
        Preference(
            icon = Icons.Default.Tune,
            title = stringRes(R.string.dictate__providers_field_realtime_model),
            summary = active.realtimeModel.ifBlank {
                stringRes(R.string.dictate__realtime_model_provider_default, "model" to (default ?: "—"))
            },
            onClick = { modelDialog = true },
        )
        if (modelDialog) {
            RealtimeModelDialog(
                models = activePreset.curatedRealtimeModels,
                default = default,
                current = active.realtimeModel,
                onConfirm = { model ->
                    scope.launch {
                        prefs.dictate.providerAccounts.set(
                            prefs.dictate.providerAccounts.get().edit(active.providerId) {
                                it.copy(realtimeModel = model)
                            },
                        )
                    }
                    modelDialog = false
                },
                onDismiss = { modelDialog = false },
            )
        }
    }

    if (providerDialog) {
        var tmp by remember { mutableStateOf(if (following) "" else pickedId) }
        JetPrefAlertDialog(
            scrollModifier = florisDialogScroll(),
            title = stringRes(R.string.dictate__realtime_provider_title),
            confirmLabel = stringRes(R.string.action__ok),
            onConfirm = {
                scope.launch { prefs.dictate.realtimeProviderId.set(tmp) }
                providerDialog = false
            },
            dismissLabel = stringRes(R.string.action__cancel),
            onDismiss = { providerDialog = false },
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Column {
                RadioRow(followLabel, selected = tmp.isBlank()) { tmp = "" }
                candidates.forEach { account ->
                    RadioRow(nameOf(account), selected = tmp == account.providerId) { tmp = account.providerId }
                }
                if (candidates.isEmpty()) {
                    Text(
                        text = stringRes(R.string.dictate__realtime_provider_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** Provider default, one of the curated streaming models, or any id typed in (stored trimmed; blank = default). */
@Composable
private fun RealtimeModelDialog(
    models: List<String>,
    default: String?,
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var tmp by remember { mutableStateOf(current) }
    var custom by remember { mutableStateOf(if (current.isNotBlank() && current !in models) current else "") }
    JetPrefAlertDialog(
        scrollModifier = florisDialogScroll(),
        title = stringRes(R.string.dictate__providers_field_realtime_model),
        confirmLabel = stringRes(R.string.action__ok),
        onConfirm = { onConfirm(tmp.trim()) },
        dismissLabel = stringRes(R.string.action__cancel),
        onDismiss = onDismiss,
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Column {
            RadioRow(
                stringRes(R.string.dictate__realtime_model_provider_default, "model" to (default ?: "—")),
                selected = tmp.isBlank(),
            ) { tmp = "" }
            models.forEach { model -> RadioRow(model, selected = tmp == model) { tmp = model } }
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it; tmp = it },
                label = { Text(stringRes(R.string.dictate__realtime_model_custom)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 12.dp))
        Text(text = label)
    }
}
