// B2-1：数据层模块——Room + DataStore + Repository（详细设计 §3、§8.6）
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.zjh.timetable.data"
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
            isIncludeAndroidResources = true // Robolectric DAO 测试需要
        }
    }
}

// Robolectric 运行期拉取 android-all 测试桩默认走 maven central（本机不可达），
// 改走与工程一致的国内镜像（AGENTS.md §5）
tasks.withType<Test>().configureEach {
    systemProperty("robolectric.dependency.repo.url", "https://maven.aliyun.com/repository/public")
    systemProperty("robolectric.dependency.repo.id", "aliyun-public")
}

// Room schema JSON 导出目录（详细设计 §9.8：schema JSON 入 git）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Repository 对外暴露 domain 模型，须用 api 让下游可见
    api(project(":core:domain"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)

    // Robolectric 基于 JUnit4（domain 的 JUnit5 测试不受影响，模块间独立运行）
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
