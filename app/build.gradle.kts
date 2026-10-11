import java.io.File
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// These values are injected by the GitHub Action (see .github/workflows/uctor.yml)
val ciVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val ciVersionName = (project.findProperty("versionName") as String?) ?: "1.0.0"
val bridgeRepo = (project.findProperty("bridgeRepo") as String?) ?: ""

val keystoreFile = rootProject.file("signing/uctor.jks")
val keystoreProps = Properties().apply {
    val f = rootProject.file("signing/signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "ir.uciran.uctor"
    compileSdk = 36

    defaultConfig {
        // NEVER change applicationId, otherwise new versions will not install over old ones.
        applicationId = "ir.uciran.uctor"
        minSdk = 29
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = ciVersionName
        buildConfigField("String", "BRIDGE_REPO", "\"$bridgeRepo\"")
    }

    signingConfigs {
        create("release") {
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (keystoreFile.exists()) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    // armeabi-v7a = old 32-bit phones, arm64-v8a = new phones, universal = works everywhere
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs { pickFirsts += setOf("**/libc++_shared.so") }
        resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/OSGI-INF/MANIFEST.MF") }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Tor (C tor, Guardian Project) + pluggable transports (Lyrebird, Snowflake, ...)
    implementation("info.guardianproject:tor-android:0.4.9.6")
    implementation("info.guardianproject:jtorctl:0.4.5.7")
    implementation("com.netzarchitekten:IPtProxy:5.7.0")
    // GitHub Actions downloads hev-socks5-tunnel.aar into app/libs before Gradle runs.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}

// عیب‌یابی: متدهای واقعی hev-socks5-tunnel را در لاگ بیلد چاپ می‌کند (خطوطی که با [UC-TUN] شروع می‌شوند).
// این تسک هرگز بیلد را خراب نمی‌کند.
val inspectTunnelAar by tasks.registering {
    doLast {
        try {
            val aar = file("libs/hev-socks5-tunnel.aar")
            if (!aar.exists()) {
                println("[UC-TUN] فایل AAR پیدا نشد")
            } else {
                val outDir = layout.buildDirectory.dir("tun-inspect").get().asFile
                outDir.deleteRecursively(); outDir.mkdirs()
                val jar = File(outDir, "classes.jar")
                ZipFile(aar).use { z ->
                    println("[UC-TUN] محتویات AAR:")
                    z.entries().asSequence().forEach { println("[UC-TUN]   ${it.name}") }
                    val e = z.getEntry("classes.jar")
                    if (e != null) jar.outputStream().use { o -> z.getInputStream(e).use { it.copyTo(o) } }
                }
                if (jar.exists()) {
                    val names = ZipFile(jar).use { z ->
                        z.entries().asSequence().map { it.name }
                            .filter { it.endsWith(".class") }
                            .map { it.removeSuffix(".class").replace('/', '.') }.toList()
                    }
                    println("[UC-TUN] کلاس‌ها: $names")
                    val javap = File(System.getProperty("java.home"), "bin/javap").absolutePath
                    val cmd = listOf(javap, "-p", "-s", "-cp", jar.absolutePath) + names
                    val out = ProcessBuilder(cmd).redirectErrorStream(true).start()
                        .inputStream.bufferedReader().readText()
                    out.lines().forEach { println("[UC-TUN] $it") }
                } else {
                    println("[UC-TUN] classes.jar داخل AAR نبود")
                }
            }
        } catch (t: Throwable) {
            println("[UC-TUN] بررسی AAR ناموفق بود: ${t.message}")
        }
    }
}
tasks.named("preBuild") { dependsOn(inspectTunnelAar) }
