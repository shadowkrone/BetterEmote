package xyz.holyb.betteremote.bttv;

import com.google.gson.reflect.TypeToken;
import net.labymod.api.util.io.web.request.Request;
import net.labymod.api.util.io.web.request.Response;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BTTVSearch {
    // BTTV sends 25 results by default and at most 100 per page, more are fetched with offset
    public static final int PAGE_SIZE = 100;
    private static final String SEARCH_BACKEND = "https://api.betterttv.net/3/emotes/shared/search?query=%s&limit=" + PAGE_SIZE + "&offset=%d";
    // The emotes added to the most channels lately, BTTV pages these with a cursor instead of an offset
    private static final String TRENDING_BACKEND = "https://api.betterttv.net/3/emotes/shared/trending?limit=" + PAGE_SIZE;
    private static final String EMOTE_BACKEND = "https://api.betterttv.net/3/emotes/%s";
    // BTTV rejects requests without a betterttv.com referer
    private static final String REFERER = "https://betterttv.com/";

    // Matches a pasted emote link (betterttv.com/emotes/<id> or cdn.betterttv.net/emote/<id>/...) or a bare id
    private static final Pattern EMOTE_ID = Pattern.compile("^(?:\\S*betterttv\\.(?:com|net)/emotes?/)?([0-9a-f]{24})(?:[/?#]\\S*)?$");

    /** One page of results, starting at offset. A pasted link or id gives just that emote. */
    public static void search(String query, int offset, Consumer<List<BTTVEmote>> callback) {
      Matcher matcher = EMOTE_ID.matcher(query.trim());
      if (matcher.matches()) {
        if (offset > 0) {
          callback.accept(List.of());
          return;
        }
        Request.ofGson(BTTVEmote.class)
            .url(String.format(EMOTE_BACKEND, matcher.group(1)))
            .addHeader("Referer", REFERER)
            .async()
            .execute(response -> callback.accept(isValid(response) ? List.of(response.get()) : List.of()));
        return;
      }

      Request.ofGson(new TypeToken<List<BTTVEmote>>(){})
          .url(String.format(SEARCH_BACKEND, URLEncoder.encode(query.trim(), StandardCharsets.UTF_8), offset))
          .addHeader("Referer", REFERER)
          .async()
          .execute(response -> callback.accept(isValid(response) ? response.get() : List.of()));
    }

    /** The trending emotes in random order, shown in the emote menu before searching. */
    public static void random(Consumer<List<BTTVEmote>> callback) {
      Request.ofGson(new TypeToken<List<TrendingEntry>>(){})
          .url(TRENDING_BACKEND)
          .addHeader("Referer", REFERER)
          .async()
          .execute(response -> {
            List<BTTVEmote> emotes = new ArrayList<>();
            if (isValid(response)) {
              for (TrendingEntry entry : response.get()) {
                if (Objects.nonNull(entry) && Objects.nonNull(entry.emote)) emotes.add(entry.emote);
              }
            }
            Collections.shuffle(emotes);
            callback.accept(emotes);
          });
    }

    // Trending sends each emote wrapped with how many channels added it
    private static class TrendingEntry {
      BTTVEmote emote;
    }

    private static boolean isValid(Response<?> response) {
      return !response.hasException() && response.isPresent() && Objects.nonNull(response.get());
    }
}
