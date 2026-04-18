plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.hilt)
	alias(libs.plugins.ksp)
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
		ksp {
			arg("room.schemaLocation", "$projectDir/schemas")
		}
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

	testOptions {
		unitTests {
			isIncludeAndroidResources = true
		}
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

	// Hilt
	implementation(libs.hilt.android)
	ksp(libs.hilt.compiler)
	implementation(libs.androidx.hilt.navigation.compose)

	// Room
	implementation(libs.androidx.room.runtime)
	implementation(libs.androidx.room.ktx)
	ksp(libs.androidx.room.compiler)

	// Navigation
	implementation(libs.androidx.navigation.compose)

	// Unit tests
	testImplementation(libs.junit.jupiter.api)
	testImplementation(libs.junit.jupiter.params)
	testRuntimeOnly(libs.junit.jupiter.engine)
	testImplementation(libs.mockk)
	testImplementation(libs.turbine)
	testImplementation(libs.androidx.room.testing)
	testImplementation(libs.kotlinx.coroutines)

	// DAO tests — JUnit 4 + Robolectric + vintage bridge
	testImplementation("junit:junit:4.13.2")
	testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.3")
	testImplementation("org.robolectric:robolectric:4.14")
	testImplementation("androidx.test:core-ktx:1.6.1")
	testImplementation("androidx.test.ext:junit-ktx:1.2.1")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
