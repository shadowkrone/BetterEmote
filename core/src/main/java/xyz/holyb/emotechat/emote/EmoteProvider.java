package xyz.holyb.emotechat.emote;

import com.google.gson.JsonObject;
import net.labymod.api.util.io.web.request.Request;
import net.labymod.api.util.io.web.request.Response;
import xyz.holyb.emotechat.EmoteChatAddon;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EmoteProvider {
  // BetterEmote shares the EmoteChat servers, so emotes work between both addons
  // Backend source code is available at: https://github.com/holybaechuLabyAddons/emotechat-backend
  public static final String BACKEND_URL = "https://emotechat.hdskins.de/v1/"; // EmoteChat backend running on HDSkins server
  public static final String BACKUP_BACKEND_URL = "https://neo.emotechat.de/v1/"; // EmoteChat backend running on RappyTV's server

  public static final String EMOTE_SPLITTER = "|";

  public static final Map<String, Emote> CACHED_EMOTES = new ConcurrentHashMap<>();
  // Ids that could not be looked up, so chat messages aren't held back for them again
  private static final Set<String> FAILED_EMOTES = ConcurrentHashMap.newKeySet();

  public static boolean isFailed(String id) {
    return FAILED_EMOTES.contains(id);
  }

  public static Emote get(String id) {
    Emote emote = CACHED_EMOTES.get(id);
    if (Objects.nonNull(emote) || FAILED_EMOTES.contains(id)) return emote;

    // "|" is not a valid URL character, so the id has to be encoded
    String path = "emote/" + URLEncoder.encode(id, StandardCharsets.UTF_8);

    emote = request(BACKEND_URL + path);
    if (Objects.isNull(emote)) emote = request(BACKUP_BACKEND_URL + path);
    if (Objects.isNull(emote)) {
      FAILED_EMOTES.add(id);
      return null;
    }

    CACHED_EMOTES.put(id, emote);

    return emote;
  }

  private static Emote request(String url) {
    // Request failures don't throw, they are stored in the response instead
    Response<Emote> response = Request.ofGson(Emote.class)
        .addHeader("User-Agent", "EmoteChat for LabyMod 4")
        .url(url)
        .executeSync();

    // Words that only look like emote codes (e.g. "mcMMO") end up here, so this isn't a warning
    if (response.hasException()) {
      EmoteChatAddon.get().logger().debug("Could not fetch emote from " + url + ": " + response.exception().getMessage());
      return null;
    }

    return response.isPresent() ? response.get() : null;
  }

  public static Emote addBTTV(String id) {
    JsonObject body = new JsonObject();
    body.addProperty("provider", "BTTV");
    body.addProperty("id", id);

    Emote emote = post(BACKEND_URL + "emote", body);
    return Objects.nonNull(emote) ? emote : post(BACKUP_BACKEND_URL + "emote", body);
  }

  private static Emote post(String url, JsonObject body) {
    Response<Emote> response = Request.ofGson(Emote.class)
        .addHeader("User-Agent", "EmoteChat for LabyMod 4")
        .url(url)
        .json(body)
        .executeSync();

    if (response.hasException()) {
      EmoteChatAddon.get().logger().warn("Failed to add emote at " + url, response.exception());
      return null;
    }

    return response.isPresent() ? response.get() : null;
  }
}
