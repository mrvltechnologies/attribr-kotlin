plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "com.mrvltechnologies.attribr"
    compileSdk = 35

    defaultConfig {
        minSdk = 26          // Android 8.0 — covers 95%+ of active devices
        targetSdk = 35

        aarMetadata {
            minCompileSdk = 26
        }

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xexplicit-api=strict")
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Zero mandatory third-party dependencies.
    // Uses only Android framework APIs:
    //   - android.provider.Settings.Secure  (device ID)
    //   - android.content.SharedPreferences (persistence)
    //   - java.net.HttpURLConnection         (networking)
    //   - java.security.MessageDigest        (SHA-256)
    //   - org.json.JSONObject                (JSON — bundled in Android)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.mrvltechnologies"
            artifactId = "attribr"
            version = "1.0.0"

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("Attribr by MRVL")
                description.set("Install attribution + 30-day retention SDK for Android. Zero third-party dependencies.")
                url.set("https://github.com/mrvl/attribr-android")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("mrvltechnologies")
                        name.set("MRVL Technologies")
                        email.set("support@mrvltechnologies.com")
                    }
                }

                scm {
                    connection.set("scm:git:github.com/mrvl/attribr-android.git")
                    developerConnection.set("scm:git:ssh://github.com/mrvl/attribr-android.git")
                    url.set("https://github.com/mrvl/attribr-android")
                }
            }
        }
    }
}
