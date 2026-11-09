// B4-1：提醒调度模块——AlarmManager 精确闹钟 + WorkManager 兜底（详细设计 §6；概要设计 §6.2）
// 依赖方向：scheduler → domain/data（AGENTS.md §3；core/data 不反向依赖本模块）
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.zjh.timetable.scheduler"
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
    testOptions {
        unitTests {
            isIncludeAndroidResources = true // Robolectric 测试需要
        }
    }
}

// Robolectric 运行期拉取 android-all 测试桩默认走 maven central（本机不可达），
// 改走与工程一致的国内镜像（AGENTS.md §5），与 core/data 同款配置
tasks.withType<Test>().configureEach {
    systemProperty("robolectric.dependency.repo.url", "https://maven.aliyun.com/repository/public")
    systemProperty("robolectric.dependency.repo.id", "aliyun-public")
}

dependencies {
    implementation(project(":core:domain"))
    // 现查 Repository 取课程/学期（不缓存课表数据，全局约束 §9）
    implementation(project(":core:data"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)

    // Robolectric 基于 JUnit4（详细设计 §10 scheduler 行单测）
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    // 测试内建 in-memory Room（与 core/data 测试同款）
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.room.ktx)
}
