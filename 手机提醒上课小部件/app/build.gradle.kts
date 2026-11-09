// B1-1：壳模块——Application 装配/导航宿主/主题（详细设计 §2）
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.zjh.timetable"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zjh.timetable"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // B5-1：release 签名——keystore.properties 缺失时不出签名配置（release 退回 unsigned，
    // assembleDebug/单测不受影响）。MVP 密钥随仓库入库保证干净检出可复现出包（仓库仅本地）。
    val keystoreProps = Properties().apply {
        file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:domain"))
    // B2-3：UI 车道换装真仓储（TimetableDatabase.build + TimetableRepository）
    implementation(project(":core:data"))
    // B3-2：导入车道直连识别网关（RecognitionGateway/OpenAiVisionRecognizer/DraftCodec）
    implementation(project(":core:network"))
    // B4-1：写后钩子接线 ReminderGraph + 提醒可靠性引导（ScheduleGuard/NotificationHelper）
    implementation(project(":core:scheduler"))
    // B4-2：写后钩子追加小组件即时刷新（WidgetUpdater）+ 启动注册周期刷新/午夜日切
    implementation(project(":feature:widget"))
    // OpenAiVisionRecognizer 默认参数引用 OkHttpClient 类型，需在调用方可见
    implementation(libs.okhttp)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // B2-2：ViewModel 校验逻辑单测（JUnit5 + 协程测试，与 domain 一致）
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
