import java.io.File

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

fun hasAndroidNdkAndCmake(): Boolean {
  val sdkRoot = System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME") ?: return false
  val ndkDir = File(sdkRoot, "ndk")
  val cmakeDir = File(sdkRoot, "cmake")
  val hasNdk = ndkDir.exists() && (ndkDir.listFiles()?.isNotEmpty() == true)
  val hasCmake = cmakeDir.exists() && (cmakeDir.listFiles()?.isNotEmpty() == true)
  return hasNdk && hasCmake
}

val enableNativeBuild = (findProperty("enableNativeBuild")?.toString()?.toBoolean() == true) || hasAndroidNdkAndCmake()

android {
  namespace = "com.aistudio.vortexpc.spike"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.vortexpc.spike"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    ndk {
      abiFilters += listOf("arm64-v8a")
    }

    if (enableNativeBuild) {
      externalNativeBuild {
        cmake {
          cFlags += listOf("-std=c11", "-O2")
          arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
        }
      }
    }
  }

  flavorDimensions += "target"
  productFlavors {
    create("t28") {
      dimension = "target"
      targetSdk = 28
      applicationIdSuffix = ".t28"
      versionNameSuffix = "-t28"
    }
    create("t29") {
      dimension = "target"
      targetSdk = 29
      applicationIdSuffix = ".t29"
      versionNameSuffix = "-t29"
    }
    create("t34") {
      dimension = "target"
      targetSdk = 34
      applicationIdSuffix = ".t34"
      versionNameSuffix = "-t34"
    }
    create("t36") {
      dimension = "target"
      targetSdk = 36
      applicationIdSuffix = ".t36"
      versionNameSuffix = "-t36"
    }
  }

  if (enableNativeBuild) {
    externalNativeBuild {
      cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
      }
    }
  }

  buildTypes {
    debug {
      isMinifyEnabled = false
    }
    release {
      isMinifyEnabled = false
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
  }

  packaging {
    jniLibs {
      useLegacyPackaging = true
    }
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.core.ktx)

  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
}
