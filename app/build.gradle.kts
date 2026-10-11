import java.io.File
import java.net.HttpURLConnection
import java.net.URI
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

// فایل‌های GeoIP رسمی Tor (geoip و geoip6) را هنگام بیلد داخل assets می‌گذارد.
// بدون آن‌ها انتخاب کشور خروجی کار نمی‌کند (Tor کد کشور را نمی‌شناسد).
// اگر فایل‌ها از قبل (مثلاً دستی) داخل app/src/main/assets/geoip باشند و معتبر باشند، دوباره دانلود نمی‌شوند.
// اگر دانلود ناموفق باشد بیلد خراب نمی‌شود، ولی هشدار می‌دهد و برنامه در صفحه‌ی خطا دلیل را نشان می‌دهد.
val downloadGeoip by tasks.registering {
    doLast {
        val dir = file("src/main/assets/geoip").apply { mkdirs() }
        val repoPaths = listOf(
            "https://raw.githubusercontent.com/torproject/tor/main/src/config/%s",
            "https://github.com/torproject/tor/raw/main/src/config/%s",
            "https://gitlab.torproject.org/tpo/core/tor/-/raw/main/src/config/%s",
            "https://gitlab.torproject.org/tpo/core/tor/-/raw/release-0.4.8/src/config/%s",
            "https://raw.githubusercontent.com/torproject/tor/release-0.4.8/src/config/%s",
            "https://raw.githubusercontent.com/torproject/tor/release-0.4.7/src/config/%s",
            "https://cdn.jsdelivr.net/gh/torproject/tor@main/src/config/%s",
            "https://raw.githubusercontent.com/torproject/tor/release-0.4.5/src/config/%s",
        )
        // هر خط داده «شروع,پایان,کد‌کشور» است (آدرس‌ها عددی یا متنی)؛ فقط ساختار کلی بررسی می‌شود.
        val line = Regex("^[^,#\\s]+,[^,\\s]+,[A-Za-z?]{2}$")
        fun check(f: File, v6: Boolean): String? {
            if (!f.exists()) return "فایلی ساخته نشد"
            val min = if (v6) 50_000L else 200_000L
            if (f.length() < min) return "حجم فقط ${f.length()} بایت"
            val ok = f.useLines { ls -> ls.take(5000).any { line.matches(it.trim()) } }
            if (!ok) return "قالب خطوط ناشناخته؛ شروع فایل: " + f.useLines { ls -> ls.take(3).joinToString(" | ") { it.take(60) } }
            return null
        }
        val problems = mutableListOf<String>()
        var missing = false
        for (name in listOf("geoip", "geoip6")) {
            val v6 = name == "geoip6"
            val dest = File(dir, name)
            if (check(dest, v6) == null) { println("[UC-GEOIP] $name از قبل موجود است (${dest.length()} بایت)"); continue }
            var done = false
            loop@ for (round in 1..2) for (pattern in repoPaths) {
                val u = pattern.format(name)
                val tmp = File(dir, "$name.part")
                try {
                    val c = URI(u).toURL().openConnection() as HttpURLConnection
                    c.connectTimeout = 20_000; c.readTimeout = 90_000; c.instanceFollowRedirects = true
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 uctor-build")
                    if (c.responseCode !in 200..299) {
                        println("[UC-GEOIP] $u -> HTTP ${c.responseCode}")
                        if (round == 2) problems += "$name: HTTP ${c.responseCode} (${u.substringAfter("://").substringBefore('/')})"
                        continue
                    }
                    c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    val why = check(tmp, v6)
                    if (why == null) {
                        dest.delete(); tmp.renameTo(dest)
                        println("[UC-GEOIP] $name دانلود شد (${dest.length()} بایت) از $u")
                        done = true; break@loop
                    }
                    println("[UC-GEOIP] $u نامعتبر: $why"); tmp.delete()
                    if (round == 2) problems += "$name: $why (${u.substringAfter("://").substringBefore('/')})"
                } catch (e: Exception) {
                    println("[UC-GEOIP] $u ناموفق: ${e.javaClass.simpleName}: ${e.message}")
                    if (round == 2) problems += "$name: ${e.javaClass.simpleName} ${e.message?.take(60)} (${u.substringAfter("://").substringBefore('/')})"
                }
            }
            if (!done && !v6) missing = true
        }
        if (missing) println("::warning::[UC-GEOIP] geoip دانلود نشد؛ انتخاب کشور کار نخواهد کرد. دلایل: " + problems.take(6).joinToString(" ; "))
    }
}
tasks.named("preBuild") { dependsOn(inspectTunnelAar, downloadGeoip) }
