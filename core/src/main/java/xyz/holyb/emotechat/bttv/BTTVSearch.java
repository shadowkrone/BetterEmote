package xyz.holyb.emotechat.bttv;

import com.google.gson.reflect.TypeToken;
import net.labymod.api.util.io.web.request.Request;
import net.labymod.api.util.io.web.request.Response;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BTTVSearch {
    private static final String SEARCH_BACKEND = "https://api.betterttv.net/3/emotes/shared/search?query=%s";
    private static final String EMOTE_BACKEND = "https://api.betterttv.net/3/emotes/%s";
    // BTTV rejects requests without a betterttv.com referer
    private static final String REFERER = "https://betterttv.com/";

    // Matches a pasted emote link (betterttv.com/emotes/<id> or cdn.betterttv.net/emote/<id>/...) or a bare id
    private static final Pattern EMOTE_ID = Pattern.compile("^(?:\\S*betterttv\\.(?:com|net)/emotes?/)?([0-9a-f]{24})(?:[/?#]\\S*)?$");

    public static void search(String query, Consumer<List<BTTVEmote>> callback) {
      Matcher matcher = EMOTE_ID.matcher(query.trim());
      if (matcher.matches()) {
        Request.ofGson(BTTVEmote.class)
            .url(String.format(EMOTE_BACKEND, matcher.group(1)))
            .addHeader("Referer", REFERER)
            .async()
            .execute(response -> callback.accept(isValid(response) ? List.of(response.get()) : List.of()));
        return;
      }

      Request.ofGson(new TypeToken<List<BTTVEmote>>(){})
          .url(String.format(SEARCH_BACKEND, URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)))
          .addHeader("Referer", REFERER)
          .async()
          .execute(response -> callback.accept(isValid(response) ? response.get() : List.of()));
    }

    private static boolean isValid(Response<?> response) {
      return !response.hasException() && response.isPresent() && Objects.nonNull(response.get());
    }
}
