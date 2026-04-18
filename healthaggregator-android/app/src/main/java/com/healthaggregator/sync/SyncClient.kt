package com.healthaggregator.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class SyncCredentials(val baseUrl: String, val token: String)

sealed class SyncError(message: String) : Exception(message) {
	object NotPaired : SyncError("not_paired")
	class Network(cause: Throwable) : SyncError("network_error: ${cause.message}")
	class BadAuth : SyncError("bad_token")
	class DaemonAheadOfPhone(val daemon: Int, val phone: Int)
		: SyncError("daemon_schema_$daemon > phone_schema_$phone")
	class HttpStatus(val code: Int, body: String) : SyncError("http_$code: $body")
}

@Singleton
open class SyncClient @Inject constructor(
	private val http: OkHttpClient,
	private val json: Json,
	private val credentialsProvider: () -> SyncCredentials?,
) {
	companion object {
		private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
	}

	open suspend fun version(): VersionResponse = get("/sync/version") { VersionResponse.serializer() }

	open suspend fun push(payload: PushRequest): PushResponse =
		post("/sync/push", json.encodeToString(payload)) { PushResponse.serializer() }

	open suspend fun pull(): PullResponse = get("/sync/pull") { PullResponse.serializer() }

	open suspend fun migrate(payload: MigrateRequest): MigrateResponse =
		post("/sync/migrate", json.encodeToString(payload)) { MigrateResponse.serializer() }

	private suspend fun <T> get(path: String, deserializer: () -> kotlinx.serialization.KSerializer<T>): T = withContext(Dispatchers.IO) {
		val creds = credentialsProvider() ?: throw SyncError.NotPaired
		val req = Request.Builder()
			.url(creds.baseUrl + path)
			.addHeader("Authorization", "Bearer ${creds.token}")
			.get()
			.build()
		runCall(req, deserializer)
	}

	private suspend fun <T> post(path: String, body: String, deserializer: () -> kotlinx.serialization.KSerializer<T>): T = withContext(Dispatchers.IO) {
		val creds = credentialsProvider() ?: throw SyncError.NotPaired
		val req = Request.Builder()
			.url(creds.baseUrl + path)
			.addHeader("Authorization", "Bearer ${creds.token}")
			.post(body.toRequestBody(JSON_MEDIA))
			.build()
		runCall(req, deserializer)
	}

	private fun <T> runCall(req: Request, deserializer: () -> kotlinx.serialization.KSerializer<T>): T {
		val resp = try {
			http.newCall(req).execute()
		} catch (io: IOException) {
			throw SyncError.Network(io)
		}
		resp.use { r ->
			val bodyStr = r.body?.string() ?: ""
			if (r.code == 401) throw SyncError.BadAuth()
			if (!r.isSuccessful) throw SyncError.HttpStatus(r.code, bodyStr)
			return json.decodeFromString(deserializer(), bodyStr)
		}
	}
}
