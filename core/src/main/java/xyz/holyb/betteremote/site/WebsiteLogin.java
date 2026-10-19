package xyz.holyb.betteremote.site;

import com.google.gson.JsonObject;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.session.Session;
import net.labymod.api.util.concurrent.task.Task;
import net.labymod.api.util.io.web.request.Request;
import net.labymod.api.util.io.web.request.Response;
import xyz.holyb.betteremote.BetterEmoteAddon;
import xyz.holyb.betteremote.emote.EmoteProvider;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Logs the player in on the website. Like a Minecraft server, the website asks Mojang whether the
 * player joined with a random server id, which proves they own the account without ever seeing
 * the access token. The website then either returns a one-time link for the browser, or approves
 * a code shown on a website profile, which links this Minecraft account to that profile.
 */
public final class WebsiteLogin {
  private static final String USER_AGENT = "BetterEmote for LabyMod 4";

  private WebsiteLogin() {
  }

  /** Both callbacks run on the render thread, finished runs once the login succeeded or failed. */
  public static void openWebsite(Consumer<Component> status, Runnable finished) {
    BetterEmoteAddon addon = BetterEmoteAddon.get();
    status.accept(Component.translatable("emote.menu.website.loggingIn", NamedTextColor.GRAY));

    run(() -> {
      // A saved token skips the Mojang check, it is dropped when the website no longer accepts it
      JsonObject linked = postWithToken(new JsonObject());
      if (Objects.nonNull(linked) && linked.has("loginUrl")) return Result.success(linked.get("loginUrl").getAsString(), null);
      return mojangLogin(null);
    }, result -> {
      status.accept(Component.translatable("emote.menu.website.opened", NamedTextColor.GREEN));
      addon.labyAPI().minecraft().chatExecutor().openUrl(result.loginUrl);
    }, status, finished);
  }

  /** Links this Minecraft account to the website account that shows this code. */
  public static void enterCode(String code, Consumer<Component> status, Runnable finished) {
    status.accept(Component.translatable("emote.menu.website.checkingCode", NamedTextColor.GRAY));

    run(() -> {
      JsonObject body = new JsonObject();
      body.addProperty("code", code);
      JsonObject linked = postWithToken(body);
      if (Objects.nonNull(linked) && linked.has("approved")) return codeResult(linked, null);
      return mojangLogin(code);
    }, result -> status.accept(Component.translatable("emote.menu.website.codeApproved", NamedTextColor.GREEN)), status, finished);
  }

  /** The website's answer to a code. A rejected code still keeps a new token, the account was proven. */
  private static Result codeResult(JsonObject answer, String token) {
    if (answer.has("approved") && answer.get("approved").getAsBoolean()) return new Result(null, token, null);

    String reason = answer.has("reason") ? answer.get("reason").getAsString() : "";
    String error = "taken".equals(reason) ? "emote.menu.website.minecraftTaken" : "emote.menu.website.invalidCode";
    return new Result(null, token, error);
  }

  /**
   * Runs the login off the render thread, then saves a new token and hands the result to
   * onSuccess, or shows the error, back on the render thread.
   */
  private static void run(Supplier<Result> login, Consumer<Result> onSuccess,
      Consumer<Component> status, Runnable finished) {
    BetterEmoteAddon addon = BetterEmoteAddon.get();
    CompletableFuture.supplyAsync(login).exceptionally(e -> {
      addon.logger().warn("Website login failed: " + e.getMessage());
      return Result.error("emote.menu.website.unreachable");
    }).thenAccept(result -> Task.builder(() -> {
      finished.run();
      if (Objects.nonNull(result.token)) {
        addon.configuration().websiteToken = result.token;
        addon.saveConfiguration();
      }
      if (Objects.nonNull(result.error)) {
        status.accept(Component.translatable(result.error, NamedTextColor.RED));
        return;
      }
      onSuccess.accept(result);
    }).build().executeOnRenderThread());
  }

  /** Calls api/auth/link with the saved token. Null when there is no token or it was rejected. */
  private static JsonObject postWithToken(JsonObject body) {
    String token = BetterEmoteAddon.get().configuration().websiteToken;
    if (Objects.isNull(token) || token.isEmpty()) return null;
    return post("api/auth/link", body, token);
  }

  /** Proves the account through Mojang. With a code, the website also answers whether it linked it. */
  private static Result mojangLogin(String code) {
    BetterEmoteAddon addon = BetterEmoteAddon.get();
    Session session = addon.labyAPI().minecraft().sessionAccessor().getSession();
    if (Objects.isNull(session) || !session.isPremium()) return Result.error("emote.menu.website.notPremium");

    JsonObject challenge = post("api/auth/challenge", new JsonObject(), null);
    if (Objects.isNull(challenge) || !challenge.has("serverId")) return Result.error("emote.menu.website.unreachable");
    String serverId = challenge.get("serverId").getAsString();

    Boolean joined = addon.labyAPI().minecraft().authenticator().joinServer(session, serverId).join();
    if (!Boolean.TRUE.equals(joined)) return Result.error("emote.menu.website.mojang");

    JsonObject body = new JsonObject();
    body.addProperty("username", session.getUsername());
    body.addProperty("serverId", serverId);
    if (Objects.nonNull(code)) body.addProperty("code", code);
    JsonObject verified = post("api/auth/verify", body, null);
    if (Objects.isNull(verified) || !verified.has("loginUrl")) return Result.error("emote.menu.website.mojang");

    String token = verified.get("token").getAsString();
    if (Objects.nonNull(code)) return codeResult(verified, token);
    return Result.success(verified.get("loginUrl").getAsString(), token);
  }

  private static JsonObject post(String path, JsonObject body, String token) {
    var request = Request.ofGson(JsonObject.class)
        .addHeader("User-Agent", USER_AGENT)
        .url(EmoteProvider.SITE_URL + path)
        .json(body);
    if (Objects.nonNull(token)) request.addHeader("Authorization", "Bearer " + token);

    Response<JsonObject> response = request.executeSync();
    if (response.hasException()) {
      BetterEmoteAddon.get().logger().debug("Website request to " + path + " failed: " + response.exception().getMessage());
      return null;
    }
    return response.isPresent() ? response.get() : null;
  }

  private record Result(String loginUrl, String token, String error) {
    static Result success(String loginUrl, String token) {
      return new Result(loginUrl, token, null);
    }

    static Result error(String key) {
      return new Result(null, null, key);
    }
  }
}
