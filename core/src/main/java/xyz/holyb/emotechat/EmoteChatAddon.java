package xyz.holyb.emotechat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.labymod.api.Laby;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.models.addon.annotation.AddonMain;
import xyz.holyb.emotechat.bttv.BTTVEmote;
import xyz.holyb.emotechat.emote.Emote;
import xyz.holyb.emotechat.emote.EmoteProvider;
import xyz.holyb.emotechat.listener.ChatMessageSendListener;
import xyz.holyb.emotechat.listener.ChatReceiveListener;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

@AddonMain
public class EmoteChatAddon extends LabyAddon<EmoteChatConfiguration> {
  private static EmoteChatAddon instance;

  public EmoteChatAddon(){
    instance = this;
  }

  public static EmoteChatAddon get(){
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

    this.logger().info("Enabled BetterEmote v" + this.addonInfo().getVersion());
  }

  @Override
  protected Class<EmoteChatConfiguration> configurationClass() {
    return EmoteChatConfiguration.class;
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
