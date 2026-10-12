plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.university.timetable"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.university.timetable"
        minSdk = 26
        targetSdk = 36
        versionCode = 216
        versionName = "2.1.6"
        vectorDrawables { useSupportLibrary = true }
        resourceConfigurations += listOf("zh", "en")
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/university-timetable.jks")
            storePassword = "timetable2026"
            keyAlias = "timetable"
            keyPassword = "timetable2026"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
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
