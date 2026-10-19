package xyz.holyb.betteremote.site;

import com.google.gson.reflect.TypeToken;
import net.labymod.api.util.io.web.request.Request;
import xyz.holyb.betteremote.emote.EmoteProvider;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Lists the emotes uploaded on the website, newest first with the official ones on top. */
public final class SiteEmoteSearch {
  private static final String USER_AGENT = "BetterEmote for LabyMod 4";

  // The website returns at most this many emotes per request
  public static final int PAGE_SIZE = 100;

  public enum Filter {
    ALL(null), OFFICIAL("official"), COMMUNITY("community");

    private final String query;

    Filter(String query) {
      this.query = query;
    }

    public String translationKey() {
      return "emote.browse.filter." + this.name().toLowerCase();
    }
  }

  private SiteEmoteSearch() {
  }

  /** The callback gets null when the website could not be reached. */
  public static void search(String query, Filter filter, int offset, Consumer<List<SiteEmote>> callback) {
    StringBuilder url = new StringBuilder(EmoteProvider.SITE_URL).append("api/emotes?offset=").append(offset);
    if (!query.isBlank()) url.append("&q=").append(URLEncoder.encode(query.trim(), StandardCharsets.UTF_8));
    if (Objects.nonNull(filter.query)) url.append("&filter=").append(filter.query);

    Request.ofGson(new TypeToken<List<SiteEmote>>(){})
        .url(url.toString())
        .addHeader("User-Agent", USER_AGENT)
        .async()
        .execute(response -> callback.accept(
            !response.hasException() && response.isPresent() ? response.get() : null));
  }
}
