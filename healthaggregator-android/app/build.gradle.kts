plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.kotlin.compose)
}

android {
	namespace = "com.healthaggregator"
	compileSdk = 36

	defaultConfig {
		applicationId = "com.healthaggregator"
		// Health Connect's Personal Health Record (clinical FHIR) API requires Android 16+ for the stable surface.
		minSdk = 34
		targetSdk = 36
		versionCode = 1
		versionName = "0.1.0"
	}

	buildTypes {
		release {
			isMinifyEnabled = false
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	kotlinOptions {
		jvmTarget = "17"
		freeCompilerArgs += listOf(
			"-opt-in=androidx.health.connect.client.ExperimentalPersonalHealthRecordApi"
		)
	}

	buildFeatures {
		compose = true
	}

	packaging {
		resources {
			excludes += "/META-INF/{AL2.0,LGPL2.1}"
		}
	}
}

dependencies {
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation(libs.androidx.activity.compose)
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.ui)
	implementation(libs.androidx.ui.graphics)
	implementation(libs.androidx.ui.tooling.preview)
	implementation(libs.androidx.material3)
	implementation(libs.androidx.material.icons.extended)
	implementation(libs.androidx.datastore.preferences)

	implementation(libs.health.connect)
	implementation(libs.okhttp)
	implementation(libs.kotlinx.coroutines)
}
