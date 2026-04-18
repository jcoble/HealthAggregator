package com.healthaggregator.bridge

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * POSTs a FHIR bundle to the Mac's HealthAggregator API.
 *
 * Security posture: the API on the Mac uses a self-signed cert by default. This
 * client trusts all certs + all hostnames because this bridge is intended for
 * LAN-only, single-user, personal-device use. Do not publish this APK or run
 * the bridge over untrusted networks.
 */
class UploadClient(private val apiBase: String) {

	private val client: OkHttpClient = buildTrustAllClient()

	suspend fun uploadBundle(
		bundleJson: String,
		sourceId: String,
		fileName: String = "common-health-bridge.json"
	): Result {
		return withContext(Dispatchers.IO) {
			val media = "application/fhir+json".toMediaType()
			val body = MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart(
					name = "file",
					filename = fileName,
					body = bundleJson.toRequestBody(media)
				)
				.build()

			val url = "$apiBase/api/imports?source=${java.net.URLEncoder.encode(sourceId, "UTF-8")}"
			val req = Request.Builder()
				.url(url)
				.post(body)
				.build()

			try {
				client.newCall(req).execute().use { resp ->
					val text = resp.body?.string() ?: ""
					if (resp.isSuccessful) Result.Success(text)
					else Result.Failure("HTTP ${resp.code}: $text")
				}
			} catch (e: Exception) {
				Log.e("UploadClient", "Upload failed", e)
				Result.Failure(e.message ?: "Unknown error")
			}
		}
	}

	sealed interface Result {
		data class Success(val responseBody: String) : Result
		data class Failure(val message: String) : Result
	}

	private fun buildTrustAllClient(): OkHttpClient {
		val trustAll = object : X509TrustManager {
			override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
			override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
			override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
		}
		val ssl = SSLContext.getInstance("TLS").apply {
			init(null, arrayOf(trustAll), SecureRandom())
		}
		return OkHttpClient.Builder()
			.sslSocketFactory(ssl.socketFactory, trustAll)
			.hostnameVerifier(HostnameVerifier { _, _ -> true })
			.connectTimeout(30, TimeUnit.SECONDS)
			.readTimeout(60, TimeUnit.SECONDS)
			.writeTimeout(60, TimeUnit.SECONDS)
			.build()
	}
}
