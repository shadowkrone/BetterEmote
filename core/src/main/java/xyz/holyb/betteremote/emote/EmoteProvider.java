package xyz.holyb.betteremote.emote;

import com.google.gson.JsonObject;
import net.labymod.api.util.io.web.exception.WebRequestException;
import net.labymod.api.util.io.web.request.Request;
import net.labymod.api.util.io.web.request.Response;
import xyz.holyb.betteremote.BetterEmoteAddon;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EmoteProvider {
  // Our own server. It passes lookups it doesn't know on to the EmoteChat servers, so emotes
  // work between both addons, and it can still add BTTV emotes when the EmoteChat servers can't
  public static final String SITE_URL = "https://betteremote.net/";
  public static final String BACKEND_URL = SITE_URL + "v1/";
  // Used directly only when our server is unreachable
  // Backend source code is available at: https://github.com/holybaechuLabyAddons/emotechat-backend
  private static final List<String> FALLBACK_URLS = List.of(
      "https://emotechat.hdskins.de/v1/", // EmoteChat backend running on HDSkins server
      "https://neo.emotechat.de/v1/" // EmoteChat backend running on RappyTV's server
  );
  private static final String USER_AGENT = "BetterEmote for LabyMod 4";

  public static final String EMOTE_SPLITTER = "|";

  public static final Map<String, Emote> CACHED_EMOTES = new ConcurrentHashMap<>();
  // Ids that could not be looked up, so chat messages aren't held back for them again
  private static final Set<String> FAILED_EMOTES = ConcurrentHashMap.newKeySet();

  public static boolean isFailed(String id) {
    return FAILED_EMOTES.contains(id);
  }

  /** Emotes in a chat message that are already looked up, in the order they appear, without duplicates. */
  public static List<Emote> findCached(String text) {
    Map<String, Emote> found = new LinkedHashMap<>();
    for (String word : text.split(" ")) {
      Emote parsed = Emote.parse(word);
      if (Objects.isNull(parsed)) continue;

      Emote emote = CACHED_EMOTES.get(parsed.id);
      if (Objects.nonNull(emote) && Objects.nonNull(emote.provider)) found.putIfAbsent(emote.id, emote);
    }
    return new ArrayList<>(found.values());
  }

  public static Emote get(String id) {
    Emote emote = CACHED_EMOTES.get(id);
    if (Objects.nonNull(emote) || FAILED_EMOTES.contains(id)) return emote;

    // "|" is not a valid URL character, so the id has to be encoded
    String path = "emote/" + URLEncoder.encode(id, StandardCharsets.UTF_8);

    // Our server already asks the EmoteChat servers, so they are only asked when it is unreachable
    Response<Emote> response = request(BACKEND_URL + path);
    emote = result(response);
    if (Objects.isNull(emote) && serverFailed(response)) {
      for (String fallback : FALLBACK_URLS) {
        emote = result(request(fallback + path));
        if (Objects.nonNull(emote)) break;
      }
    }

    if (Objects.isNull(emote)) {
      FAILED_EMOTES.add(id);
      return null;
    }

    CACHED_EMOTES.put(id, emote);

    return emote;
  }

  private static Response<Emote> request(String url) {
    // Request failures don't throw, they are stored in the response instead
    return Request.ofGson(Emote.class)
        .addHeader("User-Agent", USER_AGENT)
        .url(url)
        .executeSync();
  }

  private static Emote result(Response<Emote> response) {
    // Words that only look like emote codes (e.g. "mcMMO") end up here, so this isn't a warning
    if (response.hasException()) {
      BetterEmoteAddon.get().logger().debug("Could not fetch emote: " + response.exception().getMessage());
      return null;
    }

    return response.isPresent() ? response.get() : null;
  }

  // No answer at all, or a server error. A 404 means the emote doesn't exist anywhere
  private static boolean serverFailed(Response<Emote> response) {
    int status = response.hasException() && response.exception() instanceof WebRequestException exception
        ? exception.getResponseCode()
        : response.getStatusCode();
    return status <= 0 || status >= 500;
  }

  public static Emote addBTTV(String id) {
    JsonObject body = new JsonObject();
    body.addProperty("provider", "BTTV");
    body.addProperty("id", id);

    // Our server asks the EmoteChat servers first and registers the emote itself when they fail
    Response<Emote> response = post(BACKEND_URL + "emote", body);
    Emote emote = response.isPresent() && !response.hasException() ? response.get() : null;
    if (Objects.nonNull(emote) || !serverFailed(response)) return emote;

    for (String fallback : FALLBACK_URLS) {
      Response<Emote> fallbackResponse = post(fallback + "emote", body);
      if (fallbackResponse.isPresent() && !fallbackResponse.hasException()) return fallbackResponse.get();
    }
    return null;
  }

  private static Response<Emote> post(String url, JsonObject body) {
    Response<Emote> response = Request.ofGson(Emote.class)
        .addHeader("User-Agent", USER_AGENT)
        .url(url)
        .json(body)
        .executeSync();

    if (response.hasException()) {
      BetterEmoteAddon.get().logger().warn("Failed to add emote at " + url + ": " + response.exception().getMessage());
    }

    return response;
  }
}
