package com.healthaggregator.ai

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.util.SecureStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SecureStorageTest {

	@Ignore("EncryptedSharedPreferences incompatible with Robolectric keystore - covered by device smoke")
	@Test
	fun defaults_areFalseAndDefaultModel() {
		val s = SecureStorage(ApplicationProvider.getApplicationContext())
		assertNull(s.openAiApiKey)
		assertFalse(s.dataSharingEnabled)
		assertEquals("gpt-5", s.selectedModel)
		assertFalse(s.disclaimerAcknowledged)
	}

	@Ignore("EncryptedSharedPreferences incompatible with Robolectric keystore - covered by device smoke")
	@Test
	fun apiKey_roundTrip() {
		val s = SecureStorage(ApplicationProvider.getApplicationContext())
		s.openAiApiKey = "sk-test-abc"
		assertEquals("sk-test-abc", s.openAiApiKey)
	}

	@Ignore("EncryptedSharedPreferences incompatible with Robolectric keystore - covered by device smoke")
	@Test
	fun apiKey_blankClears() {
		val s = SecureStorage(ApplicationProvider.getApplicationContext())
		s.openAiApiKey = "sk-x"
		s.openAiApiKey = ""
		assertNull(s.openAiApiKey)
	}

	@Ignore("EncryptedSharedPreferences incompatible with Robolectric keystore - covered by device smoke")
	@Test
	fun selectedModel_persists() {
		val s = SecureStorage(ApplicationProvider.getApplicationContext())
		s.selectedModel = "gpt-5.4"
		assertEquals("gpt-5.4", s.selectedModel)
	}

	@Ignore("EncryptedSharedPreferences incompatible with Robolectric keystore - covered by device smoke")
	@Test
	fun disclaimer_persists() {
		val s = SecureStorage(ApplicationProvider.getApplicationContext())
		s.disclaimerAcknowledged = true
		assertTrue(s.disclaimerAcknowledged)
	}
}
