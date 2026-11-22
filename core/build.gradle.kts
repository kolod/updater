plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: no android.* imports are possible here (constitution, Principle V).
// org.json is provided by the Android platform at runtime, so it is compile-only.
dependencies {
    compileOnly(libs.org.json)
    testImplementation(libs.org.json)
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
