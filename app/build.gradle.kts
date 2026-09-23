import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins { kotlin("jvm"); kotlin("plugin.serialization"); id("org.jetbrains.kotlin.plugin.compose"); id("org.jetbrains.compose") }

kotlin {
    jvmToolchain(21)
    compilerOptions.freeCompilerArgs.addAll(
        "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
    )
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.11.0-alpha07")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
}

compose.desktop {
    application {
        mainClass = "com.yumi.launcher.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Yumi Launcher"
            packageVersion = "0.1.0"
        }
    }
}

// A frozen copy of the app and its libraries: long headless checks (`--server-test`, `--launch-test`)
// run from it with plain `java -cp`, so edits made while they run cannot break them half-way.
tasks.register<Sync>("testBundle") {
    from(configurations.runtimeClasspath)
    from(tasks.jar)
    into(layout.buildDirectory.dir("test-bundle"))
}
