import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ---- 发布签名配置 ----
// 密钥库与口令**故意不入库**（见 .gitignore）：一旦公开，任何人都能签出与正式版
// 同签名的 APK，已安装的用户会被静默「升级」到伪造版本。
//
// 本机要出正式包，就在项目根目录放一个 keystore.properties（同样已被忽略）：
//     storeFile=keystore/university-timetable.jks
//     storePassword=……
//     keyAlias=timetable
//     keyPassword=……
// 读不到该文件时**退回 debug 签名**：这样任何人 clone 下来都能直接
// `gradlew assembleRelease` 跑通构建，只是产物签名不同、不能覆盖安装正式版。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = !keystoreProperties.getProperty("storeFile").isNullOrBlank()

android {
    namespace = "com.university.timetable"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.university.timetable"
        minSdk = 26
        targetSdk = 36
        versionCode = 217
        versionName = "2.1.7"
        vectorDrawables { useSupportLibrary = true }
        resourceConfigurations += listOf("zh", "en")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        // 用于在「关于」中显示真实版本号
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*"
        )
    }

    lint {
        abortOnError = false
    }

    testOptions {
        unitTests {
            // 让 android.util.Log 这类桩方法返回默认值而不是抛
            // "Method ... not mocked"，纯 JVM 测试才能覆盖仓库/刷新器的逻辑。
            isReturnDefaultValues = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    // 基础
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose UI
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // 导航
    implementation(libs.androidx.navigation.compose)

    // 数据持久化
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // 后台任务与提醒
    implementation(libs.androidx.work.runtime.ktx)

    // 桌面小组件（Glance，支持 LazyColumn 滚动）
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // 文件访问
    implementation(libs.androidx.documentfile)

    // OCR：Google ML Kit 中文文字识别（免费、离线）
    implementation(libs.mlkit.text.recognition)

    // Java 8+ 时间 API 向下兼容
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // 单元测试（排课逻辑 / 导入解析器 / 刷新合并与主题）
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
