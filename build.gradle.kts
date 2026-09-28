plugins {
    id("net.labymod.labygradle")
    id("net.labymod.labygradle.addon")
}

val versions = providers.gradleProperty("net.labymod.minecraft-versions").get().split(";")

group = "xyz.holyb"
version = providers.environmentVariable("VERSION").getOrElse("1.2.0")

labyMod {
    // Must differ from EmoteChat's "xyz.holyb", otherwise both addons generate the same
    // xyz.holyb.core.generated classes and LabyMod loads EmoteChat's copy for ours
    defaultPackageName = "xyz.holyb.betteremote"

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
        namespace = "emote"
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
