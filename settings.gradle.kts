pluginManagement {
    val quarkusPluginVersion: String by settings
    plugins {
        id("io.quarkus") version quarkusPluginVersion
    }
}
rootProject.name = "ExpenseLine"
