plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 正式签名：证书与口令放在工程根目录的 keystore.properties（本地文件，不进版本库）。
// 缺少该文件时不配 release 签名，保证 clone 后仍能编译。
val signingProps: Map<String, String> = mutableMapOf<String, String>().apply {
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) {
        propsFile.readLines().forEach { line ->
            val text = line.trim()
            val sep = text.indexOf('=')
            if (text.isNotEmpty() && !text.startsWith("#") && sep > 0) {
                put(text.substring(0, sep).trim(), text.substring(sep + 1).trim())
            }
        }
    }
}
val hasSigning = signingProps.containsKey("storeFile")

android {
    namespace = "com.deviceledger.app"
    // 本机 SDK 只安装了 android-37 平台与 36.0.0 构建工具，按实际环境对齐
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.deviceledger.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 12
        versionName = "1.8.0"
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(signingProps.getOrDefault("storeFile", ""))
                storePassword = signingProps.getOrDefault("storePassword", "")
                keyAlias = signingProps.getOrDefault("keyAlias", "")
                keyPassword = signingProps.getOrDefault("keyPassword", "")
            }
        }
        // 沿用最早那批包的证书：老设备上装的是这个签名，换证书就装不上，
        // 所以单独出一个"覆盖升级包"，让它们能原地升上来、数据不丢。
        if (signingProps.containsKey("legacyStoreFile")) {
            create("legacy") {
                storeFile = file(signingProps.getOrDefault("legacyStoreFile", ""))
                storePassword = signingProps.getOrDefault("legacyStorePassword", "")
                keyAlias = signingProps.getOrDefault("legacyKeyAlias", "")
                keyPassword = signingProps.getOrDefault("legacyKeyPassword", "")
            }
        }
    }

    buildTypes {
        release {
            // 正式发布包：不带 debuggable 标记，用上面的正式证书签名
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // 覆盖升级包：内容和正式包完全一致，只是换成老证书签名
        if (signingConfigs.findByName("legacy") != null) {
            create("upgrade") {
                initWith(getByName("release"))
                signingConfig = signingConfigs.findByName("legacy")
            }
        }
        debug {
            // 调试包保留默认调试签名，与正式包区分
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
        viewBinding = true
    }

    // 资源命名与语言配置：只保留中文与默认，避免多语言资源缺失告警
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
