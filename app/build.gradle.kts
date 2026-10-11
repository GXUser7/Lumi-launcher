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
            // The folder and the .exe; the name people see comes from the installer (installer/LumiLauncher.iss).
            packageName = "LumiLauncher"
            packageVersion = "1.1.0"
            description = "Lumi Launcher"
            vendor = "Lumi"
            // The whole JDK: the launcher reaches for java.net.http, prefs, management and TLS, and a
            // module left out by jlink only shows up as a crash on someone else's computer.
            includeAllModules = true
            windows {
                iconFile.set(rootProject.file("scripts/lumi.ico"))
                upgradeUuid = "80eb9d2a-572b-40d7-a735-fb3483246b50"
            }
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
