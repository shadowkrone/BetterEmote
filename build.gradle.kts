plugins {
    id("net.labymod.labygradle")
    id("net.labymod.labygradle.addon")
}

val versions = providers.gradleProperty("net.labymod.minecraft-versions").get().split(";")

group = "xyz.holyb"
version = providers.environmentVariable("VERSION").getOrElse("1.2.0")

labyMod {
    defaultPackageName = "xyz.holyb" //change this to your main package name (used by all modules)

    minecraft {
        registerVersion(versions.toTypedArray()) {
            runs {
                getByName("client") {
                    // When the property is set to true, you can log in with a Minecraft account
                    // devLogin = true
                }
            }
        }
    }

    addonInfo {
        namespace = "betteremote"
        displayName = "BetterEmote"
        author = "ShadowKrone"
        description = "Animated BetterTTV emotes in your Minecraft chat. - discord - discord.gg/FxWsjMRZrD"
        minecraftVersion = "1.19.4<*"
        version = rootProject.version.toString()
    }
}

subprojects {
    plugins.apply("net.labymod.labygradle")
    plugins.apply("net.labymod.labygradle.addon")

    group = rootProject.group
    version = rootProject.version

    extensions.findByType(JavaPluginExtension::class.java)?.apply {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
