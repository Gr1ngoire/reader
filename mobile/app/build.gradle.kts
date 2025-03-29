import java.util.Properties
import java.io.FileInputStream

val localProperties = Properties()
val localFile = rootProject.file("local.properties")
if (localFile.exists()) {
    localProperties.load(FileInputStream(localFile))
}

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.reader"
    compileSdk = 34

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.reader"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "AWS_ACCESS_KEY", "\"${localProperties.getProperty("AWS_ACCESS_KEY", "")}\"")
        buildConfigField("String", "AWS_SECRET_KEY", "\"${localProperties.getProperty("AWS_SECRET_KEY", "")}\"")
        buildConfigField("String", "AWS_REGION", "\"${localProperties.getProperty("AWS_REGION", "")}\"")
        buildConfigField("String", "AWS_BOOKS_BUCKET_NAME", "\"${localProperties.getProperty("AWS_BOOKS_BUCKET_NAME", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    viewBinding {
        enable = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.material.v190)
    implementation(libs.mhiew.android.pdf.viewer)
    implementation(libs.okhttp)
    implementation(libs.amazonaws.aws.android.sdk.s3)
    implementation(libs.aws.android.sdk.core)
    implementation(project(":opencv"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}