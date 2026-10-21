package xyz.holyb.betteremote;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.labymod.api.Laby;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.entity.player.tag.PositionType;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.screen.widget.widgets.activity.chat.ChatButtonWidget;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.models.addon.annotation.AddonMain;
import net.labymod.api.util.concurrent.task.Task;
import xyz.holyb.betteremote.activity.EmoteChatTabActivity;
import xyz.holyb.betteremote.bttv.BTTVEmote;
import xyz.holyb.betteremote.emote.Emote;
import xyz.holyb.betteremote.emote.EmoteProvider;
import xyz.holyb.betteremote.listener.ChatContextMenuListener;
import xyz.holyb.betteremote.listener.ChatMessageSendListener;
import xyz.holyb.betteremote.listener.ChatReceiveListener;
import xyz.holyb.betteremote.listener.EmoteMenuKeyListener;
import xyz.holyb.betteremote.tag.RoleRegistry;
import xyz.holyb.betteremote.tag.RoleTag;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@AddonMain
public class BetterEmoteAddon extends LabyAddon<BetterEmoteConfiguration> {
  private static BetterEmoteAddon instance;
  private boolean onlineFeaturesStarted;

  public BetterEmoteAddon(){
    instance = this;
  }

  public static BetterEmoteAddon get(){
    return instance;
  }

  @Override
  protected void preConfigurationLoad() {
    migrateOldNamespaceConfig();
  }

  @Override
  protected void enable() {
    // Nothing is downloaded or imported while the addon is switched off, turning it on starts it
    if (this.configuration().enabled().get()) this.startOnlineFeatures();
    this.configuration().enabled().addChangeListener((Boolean enabled) -> {
      if (enabled) this.startOnlineFeatures();
    });

    // Saved emotes are known up front, so they can be shown without a lookup
    for (Emote emote : this.configuration().getEmotes().values()) {
      if (Objects.nonNull(emote) && Objects.nonNull(emote.id) && Objects.nonNull(emote.provider)) {
        EmoteProvider.CACHED_EMOTES.put(emote.id, emote);
      }
    }

    this.registerSettingCategory();

    this.registerListener(new ChatReceiveListener(this));
    this.registerListener(new ChatMessageSendListener(this));
    this.registerListener(new ChatContextMenuListener(this));
    this.registerListener(new EmoteMenuKeyListener(this));

    // Button next to Autotext, Symbols and Name History in the chat input
    ChatButtonWidget chatButton = ChatButtonWidget.icon("emotes",
        Icon.texture(ResourceLocation.create("emote", "textures/icon.png")), EmoteChatTabActivity::new)
        .property(this.configuration().enabled());
    chatButton.setHoverComponent(Component.translatable("emote.chatTab.button"));
    this.labyAPI().chatProvider().chatInputService().register(chatButton);

    this.labyAPI().tagRegistry().register("betteremote_role", PositionType.ABOVE_NAME, new RoleTag(this));

    this.logger().info("Enabled BetterEmote v" + this.addonInfo().getVersion());
  }

  /** The EmoteChat import and the role list, once per game start and only while the addon is on. */
  private void startOnlineFeatures() {
    if (this.onlineFeaturesStarted) return;
    this.onlineFeaturesStarted = true;
    importEmoteChatEmotes();
    RoleRegistry.load();
  }

  @Override
  protected Class<BetterEmoteConfiguration> configurationClass() {
    return BetterEmoteConfiguration.class;
  }

  /**
   * Registers a BTTV emote with the emote server and saves it under the given name.
   * The callback runs on the render thread and gets null if the server rejected the emote.
   */
  public void addEmote(String name, String bttvId, Consumer<Emote> callback) {
    // Registering the emote is a network request, so it must not block the render thread
    CompletableFuture.supplyAsync(() -> EmoteProvider.addBTTV(bttvId)).thenAccept(serverEmote -> Task.builder(() -> {
      if (Objects.nonNull(serverEmote)) {
        EmoteProvider.CACHED_EMOTES.put(serverEmote.id, serverEmote);
        this.configuration().getEmotes().put(name, serverEmote);
        this.saveConfiguration();
      }
      callback.accept(serverEmote);
    }).build().executeOnRenderThread());
  }

  /** The name the emote is saved under, or null if it isn't saved. */
  public String savedName(Emote emote) {
    for (Entry<String, Emote> entry : this.configuration().getEmotes().entrySet()) {
      if (Objects.nonNull(entry.getValue()) && Objects.equals(entry.getValue().id, emote.id)) return entry.getKey();
    }
    return null;
  }

  /**
   * Saves an emote someone else sent in chat. It keeps its own name, with a number added when
   * the player already has a different emote with that name. Returns the name it was saved under.
   */
  public String saveEmote(Emote emote) {
    String saved = this.savedName(emote);
    if (Objects.nonNull(saved)) return saved;

    String name = this.uniqueName(emote.name());
    this.configuration().getEmotes().put(name, emote);
    this.saveConfiguration();
    return name;
  }

  /** The name, with a number added when the player already has an emote called that, e.g. "catJAM2". */
  public String uniqueName(String base) {
    String name = base;
    for (int i = 2; this.hasEmoteNamed(name); i++) name = base + i;
    return name;
  }

  /**
   * Saves the emote under a new name. Returns false when another emote already has that name.
   * Changing only the case of the name is allowed.
   */
  public boolean renameEmote(String oldName, String newName) {
    Map<String, Emote> emotes = this.configuration().getEmotes();
    Emote emote = emotes.get(oldName);
    if (Objects.isNull(emote)) return false;
    if (!oldName.equalsIgnoreCase(newName) && this.hasEmoteNamed(newName)) return false;

    emotes.remove(oldName);
    emotes.put(newName, emote);
    this.saveConfiguration();
    return true;
  }

  // Chat matches names case-insensitively, so "catjam" and "catJAM" would clash
  private boolean hasEmoteNamed(String name) {
    return this.configuration().getEmotes().keySet().stream().anyMatch(name::equalsIgnoreCase);
  }

  /**
   * The addon was published as "betteremote" before its namespace became "emote". Copies the
   * old settings over once, so players keep their emotes and settings after updating.
   */
  private void migrateOldNamespaceConfig() {
    Path configs = Path.of(Laby.labyAPI().labyModLoader().getGameDirectory().toString(), "labymod-neo", "configs");
    Path oldFile = configs.resolve("betteremote").resolve("settings.json");
    // Runs before the addon info is guaranteed to be available, so the namespace is spelled out
    Path newFile = configs.resolve("emote").resolve("settings.json");
    if (!Files.exists(oldFile) || Files.exists(newFile)) return;

    try {
      Files.createDirectories(newFile.getParent());
      Files.copy(oldFile, newFile);
      this.logger().info("Migrated settings from the old betteremote namespace");
    } catch (Exception e) {
      this.logger().warn("Could not migrate settings from the old betteremote namespace: " + e.getMessage());
    }
  }

  /**
   * Imports saved emotes from an EmoteChat installation the first time BetterEmote starts.
   * Supports both the current format and the old one that only stored BTTV emotes.
   */
  private void importEmoteChatEmotes() {
    if (!this.configuration().getEmotes().isEmpty()) return;

    Path file = Path.of(Laby.labyAPI().labyModLoader().getGameDirectory().toString(), "labymod-neo", "configs", "emotechat", "settings.json");
    if (!Files.exists(file)) return;

    // The reader must be closed before saving, otherwise Windows keeps the file locked
    JsonObject emotes;
    try (Reader reader = Files.newBufferedReader(file)) {
      JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
      if (!root.has("emotes") || !root.get("emotes").isJsonObject()) return;
      emotes = root.getAsJsonObject("emotes");
    } catch (Exception e) {
      this.logger().warn("Could not read EmoteChat configuration: " + e.getMessage());
      return;
    }

    // Old BTTV emotes have to be registered with the emote server, so the import must not block enable()
    CompletableFuture.supplyAsync(() -> convertEmoteChatEmotes(emotes)).thenAccept(imported -> {
      if (imported.isEmpty()) return;

      Task.builder(() -> {
        for (Entry<String, Emote> entry : imported.entrySet()) {
          Emote emote = entry.getValue();
          if (Objects.nonNull(emote.provider)) EmoteProvider.CACHED_EMOTES.put(emote.id, emote);
          // The player may have added emotes while the import was running, those take priority
          this.configuration().getEmotes().putIfAbsent(entry.getKey(), emote);
        }
        this.saveConfiguration();
        this.logger().info("Imported " + imported.size() + " emotes from EmoteChat");
      }).build().executeOnRenderThread();
    });
  }

  private Map<String, Emote> convertEmoteChatEmotes(JsonObject emotes) {
    Gson gson = new Gson();
    Map<String, Emote> imported = new HashMap<>();
    for (Entry<String, JsonElement> entry : emotes.entrySet()) {
      if (!entry.getValue().isJsonObject()) continue;
      JsonObject value = entry.getValue().getAsJsonObject();

      Emote emote = null;
      if (value.has("_id")) {
        emote = gson.fromJson(value, Emote.class);
      } else if (value.has("user") && value.has("id")) {
        emote = EmoteProvider.addBTTV(gson.fromJson(value, BTTVEmote.class).id);
      }

      if (Objects.nonNull(emote) && Objects.nonNull(emote.id)) imported.put(entry.getKey(), emote);
    }

    return imported;
  }
}
