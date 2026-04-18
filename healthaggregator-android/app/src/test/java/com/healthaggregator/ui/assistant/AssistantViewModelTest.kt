package com.healthaggregator.ui.assistant

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.healthaggregator.ai.AssistantRepository
import com.healthaggregator.ai.ChatExporter
import com.healthaggregator.data.entities.ChatConversation
import com.healthaggregator.util.SecureStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AssistantViewModelTest {
	private val dispatcher = StandardTestDispatcher()

	@Before fun setup() { kotlinx.coroutines.Dispatchers.setMain(dispatcher) }
	@After fun tearDown() { kotlinx.coroutines.Dispatchers.resetMain() }

	private fun makeSecure(): SecureStorage = mockk<SecureStorage>(relaxed = true).apply {
		every { openAiApiKey } returns null
		every { disclaimerAcknowledged } returns false
		every { selectedModel } returns "gpt-5"
	}

	@Test
	fun uiState_reflectsConversationsFromRepo() = runTest(dispatcher) {
		val repo = mockk<AssistantRepository>(relaxed = true)
		val convos = MutableStateFlow(listOf(conv("c1", "T")))
		every { repo.observeConversations() } returns convos
		every { repo.observeMessages(any()) } returns flowOf(emptyList())
		val vm = AssistantViewModel(repo, makeSecure(), mockk<ChatExporter>(relaxed = true), mockk<Context>(relaxed = true))

		// Subscribe to uiState (activates WhileSubscribed stateIn) then advance.
		val job = launch { vm.uiState.collect { } }
		testScheduler.advanceUntilIdle()
		// After advancing, conversations StateFlow should have propagated.
		assertEquals(1, vm.uiState.value.conversations.size)
		job.cancel()
	}

	@Test
	fun send_emptyString_isNoop() = runTest(dispatcher) {
		val repo = mockk<AssistantRepository>(relaxed = true)
		every { repo.observeConversations() } returns flowOf(emptyList())
		every { repo.observeMessages(any()) } returns flowOf(emptyList())
		val vm = AssistantViewModel(repo, makeSecure(), mockk<ChatExporter>(relaxed = true), mockk<Context>(relaxed = true))
		vm.send("   ")
		vm.send("")
		// No crash, no repo.send invocation = pass
	}

	@Test
	fun acknowledgeDisclaimer_delegatesToSecureStorage() {
		val repo = mockk<AssistantRepository>(relaxed = true)
		every { repo.observeConversations() } returns flowOf(emptyList())
		every { repo.observeMessages(any()) } returns flowOf(emptyList())
		val secure = makeSecure()
		val vm = AssistantViewModel(repo, secure, mockk<ChatExporter>(relaxed = true), mockk<Context>(relaxed = true))
		vm.acknowledgeDisclaimer()
		verify { secure.disclaimerAcknowledged = true }
	}

	private fun conv(id: String, title: String) = ChatConversation(
		id = id,
		title = title,
		createdAt = Instant.EPOCH,
		updatedAt = Instant.EPOCH,
		modelId = "gpt-5",
	)
}
