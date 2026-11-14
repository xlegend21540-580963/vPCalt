import java.io.File

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.dagger.hilt.android)
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
  namespace = "com.aistudio.vortexpc.nativebridge"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 26
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")

    if (enableNativeBuild) {
      ndk {
        abiFilters += listOf("arm64-v8a")
      }
      externalNativeBuild {
        cmake {
          cppFlags += listOf("-std=c++17", "-O2")
          arguments += listOf("-DANDROID_STL=c++_shared", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
        }
      }
    }
  }

  if (enableNativeBuild) {
    externalNativeBuild {
      cmake {
        path = file("src/main/cpp/CMakeLists.txt")
      }
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation(project(":core"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
}
