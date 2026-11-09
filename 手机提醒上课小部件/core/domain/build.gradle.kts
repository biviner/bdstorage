// B1-1：纯 Kotlin 领域模块，禁 android.*/androidx.*（详细设计 §2 依赖规则）
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit.jupiter)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
