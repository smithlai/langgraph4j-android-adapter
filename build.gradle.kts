import java.io.FileInputStream
import java.util.Properties


plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties()

if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
android {
    namespace = "com.smith.lai.langgraph4j_android_adapter"
    compileSdk = 34

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "OPENAI_API_KEY", "\"${localProperties.getProperty("openai.api.key", "")}\"")
        buildConfigField("String", "OLLAMA_URL", "\"${localProperties.getProperty("ollama.api.url", "")}\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
    buildFeatures {
//        compose = true
        buildConfig = true
    }
}

dependencies {
    // LangGraph4j dependencies
    implementation("org.bsc.langgraph4j:langgraph4j-core:1.5.8")
    implementation("org.bsc.langgraph4j:langgraph4j-langchain4j:1.5.8")
    implementation("org.bsc.langgraph4j:langgraph4j-agent-executor:1.5.8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    // LangChain4j dependencies - Moved to implementation for main
    implementation("dev.langchain4j:langchain4j:1.0.0-beta3")
    implementation("dev.langchain4j:langchain4j-open-ai:1.0.0-beta3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // For OkHttpClientAdapter

    // Ktor dependencies (optional, keep if needed)
//    implementation("io.ktor:ktor-client-core:2.3.7")
//    implementation("io.ktor:ktor-client-okhttp:2.3.7")
//    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")


    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
//    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
