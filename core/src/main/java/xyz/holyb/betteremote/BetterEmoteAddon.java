package xyz.holyb.betteremote;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.labymod.api.Laby;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.client.entity.player.tag.PositionType;
import net.labymod.api.configuration.settings.Setting;
import net.labymod.api.models.addon.annotation.AddonMain;
import net.labymod.api.util.concurrent.task.Task;
import xyz.holyb.betteremote.bttv.BTTVEmote;
import xyz.holyb.betteremote.emote.Emote;
import xyz.holyb.betteremote.emote.EmoteProvider;
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

  public BetterEmoteAddon(){
    instance = this;
  }

  public static BetterEmoteAddon get(){
    return instance;
  }

  @Override
  protected void enable() {
    importEmoteChatEmotes();

    // Saved emotes are known up front, so they can be shown without a lookup
    for (Emote emote : this.configuration().getEmotes().values()) {
      if (Objects.nonNull(emote) && Objects.nonNull(emote.id) && Objects.nonNull(emote.provider)) {
        EmoteProvider.CACHED_EMOTES.put(emote.id, emote);
      }
    }

    this.registerSettingCategory();

    this.registerListener(new ChatReceiveListener(this));
    this.registerListener(new ChatMessageSendListener(this));
    this.registerListener(new EmoteMenuKeyListener(this));

    RoleRegistry.load();
    this.labyAPI().tagRegistry().register("betteremote_role", PositionType.ABOVE_NAME, new RoleTag(this));

    this.logger().info("Enabled BetterEmote v" + this.addonInfo().getVersion());
  }

  @Override
  protected Class<BetterEmoteConfiguration> configurationClass() {
    return BetterEmoteConfiguration.class;
  }

  /**
   * Opens the BetterEmote page in the LabyMod settings.
   */
  public void openSettings() {
    // registerSettingCategory() adds the category to the core registry under the addon namespace
    Setting settings = this.labyAPI().coreSettingRegistry().findSetting(this.addonInfo().getNamespace());
    if (Objects.nonNull(settings)) this.labyAPI().showSetting(settings);
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

    if (imported.isEmpty()) return;

    this.configuration().getEmotes().putAll(imported);
    this.saveConfiguration();
    this.logger().info("Imported " + imported.size() + " emotes from EmoteChat");
  }
}
