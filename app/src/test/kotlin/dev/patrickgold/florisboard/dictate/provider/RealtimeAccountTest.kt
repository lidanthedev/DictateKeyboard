/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.provider

import kotlin.test.Test
import kotlin.test.assertEquals

class RealtimeAccountTest {

    private val groq = ProviderAccount(providerId = "groq", apiKey = "g", transcriptionModel = "whisper-large-v3-turbo")
    private val gemini = ProviderAccount(providerId = "gemini", apiKey = "k", transcriptionModel = "gemini-3.5-flash")
    private val keyring = ProviderAccounts.Empty.put(groq).put(gemini)

    @Test
    fun blankPickFollowsTheTranscriptionProviderAsBefore() {
        // What every existing install reads: the pref did not exist, so it is its default "".
        assertEquals(groq, keyring.realtimeAccount("", "groq"))
    }

    @Test
    fun aPickStreamsOnItsOwnAccountWhileBatchStaysOnTheOther() {
        val picked = keyring.edit("gemini") { it.copy(realtimeModel = "gemini-3.5-transcribe-live") }
        val rt = picked.realtimeAccount("gemini", "groq")
        assertEquals("gemini", rt.providerId)
        assertEquals("gemini-3.5-transcribe-live", rt.realtimeModel)
        // Neither batch model moved: the streaming model has its own slot.
        assertEquals("gemini-3.5-flash", rt.transcriptionModel)
        assertEquals("whisper-large-v3-turbo", picked.getOrEmpty("groq").transcriptionModel)
    }

    @Test
    fun changingTheBatchModelLeavesTheStreamingModelAlone() {
        val picked = keyring
            .edit("gemini") { it.copy(realtimeModel = "my-custom-live") }
            .edit("groq") { it.copy(transcriptionModel = "whisper-large-v3") }
        assertEquals("my-custom-live", picked.realtimeAccount("gemini", "groq").realtimeModel)
    }

    @Test
    fun aPickRemovedFromTheKeyringFallsBackToTranscription() {
        assertEquals(groq, keyring.remove("gemini").realtimeAccount("gemini", "groq"))
    }

    @Test
    fun aStoredKeyringFromBeforeTheChangeKeepsBothModels() {
        val stored = keyring.edit("gemini") { it.copy(realtimeModel = "gemini-3.5-transcribe-live") }
        val restored = ProviderAccounts.Serializer.deserialize(ProviderAccounts.Serializer.serialize(stored))
        assertEquals("gemini-3.5-transcribe-live", restored.realtimeAccount("gemini", "groq").realtimeModel)
        assertEquals("whisper-large-v3-turbo", restored.realtimeAccount("", "groq").transcriptionModel)
    }
}
