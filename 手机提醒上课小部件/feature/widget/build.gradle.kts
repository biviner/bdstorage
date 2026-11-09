// B4-2：Glance 小组件模块——2×2「下一节课」+ 4×2「今日课程」+ 刷新机制（详细设计 §7；概要设计 §6.3）
// 依赖方向：widget → domain/data（详细设计 §2）；小组件是数据库只读投影，现查现算不缓存
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.zjh.timetable.widget"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
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
    implementation(project(":core:data"))
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)

    // 状态计算为纯 Kotlin（无 Android 依赖），JUnit5 与 core/domain 一致
    testImplementation(libs.junit.jupiter)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
