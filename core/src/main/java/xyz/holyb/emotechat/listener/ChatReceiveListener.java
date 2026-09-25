package xyz.holyb.emotechat.listener;

import net.labymod.api.client.chat.ChatMessage;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.TextComponent;
import net.labymod.api.client.component.TranslatableComponent;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.component.format.Style;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.chat.ChatReceiveEvent;
import net.labymod.api.util.concurrent.task.Task;
import xyz.holyb.emotechat.EmoteChatAddon;
import xyz.holyb.emotechat.emote.Emote;
import xyz.holyb.emotechat.emote.EmoteProvider;
import xyz.holyb.emotechat.gui.AnimatedEmote;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatReceiveListener {
  // Emote lookups are network requests and must never run on the render thread
  private static final ExecutorService LOOKUP = Executors.newSingleThreadExecutor(runnable -> {
    Thread thread = new Thread(runnable, "BetterEmote lookup");
    thread.setDaemon(true);
    return thread;
  });

  private final EmoteChatAddon addon;

  public ChatReceiveListener(EmoteChatAddon addon) {
    this.addon = addon;
  }

  private Component createEmoteComponent(Emote serverEmote) {
    int quality = addon.configuration().emoteQuality().get();
    Icon icon = serverEmote.animated && addon.configuration().animatedEmotes().get()
        ? AnimatedEmote.icon(serverEmote.getImageURL(quality), serverEmote.getStaticImageURL(quality))
        : Icon.url(serverEmote.getStaticImageURL(quality));

    return Component.empty().setChildren(List.of(
        Component.icon(
            icon,
            Style.builder().color(NamedTextColor.WHITE).build(),
            addon.configuration().emoteSize().get()
        ),
        Component.text(" ")
    ));
  }

  // Replaces emote codes with images. Only cached emotes are used, so nothing blocks the render thread
  private Component replaceEmote(Component component) {
    Component result = Component.empty();
    boolean replaced = false;

    List<Component> children = new ArrayList<>(component.getChildren());
    for (int i = 0; i < children.size(); i++) {
      Component replacement = replaceEmote(children.get(i));
      if (Objects.nonNull(replacement)) {
        children.set(i, replacement);
        replaced = true;
      }
    }

    if (component instanceof TranslatableComponent translatableComponent) {
      List<Component> arguments = new ArrayList<>(translatableComponent.getArguments());
      for (int i = 0; i < arguments.size(); i++) {
        Component replacement = replaceEmote(arguments.get(i));
        if (Objects.nonNull(replacement)) {
          arguments.set(i, replacement);
          replaced = true;
        }
      }

      result = Component.translatable(translatableComponent.getKey()).arguments(arguments);
    }

    List<Component> replacement = new ArrayList<>();
    if (component instanceof TextComponent textComponent) {
      Style style = textComponent.style();
      int lastReplacement = 0;
      String[] words = textComponent.getText().split(" ");
      for (int i = 0; i < words.length; i++) {
        Emote emote = Emote.parse(words[i]);
        if (Objects.isNull(emote)) continue;

        Emote serverEmote = EmoteProvider.CACHED_EMOTES.get(emote.id);
        if (Objects.isNull(serverEmote)) continue;

        replacement.addAll(List.of(
            Component.text(String.join(" ", Arrays.copyOfRange(words, lastReplacement, i)) + " ", style),
            createEmoteComponent(serverEmote)
        ));

        lastReplacement = i + 1;
        replaced = true;
      }

      replacement.add(Component.text(String.join(" ", Arrays.copyOfRange(words, lastReplacement, words.length)), style));
    }

    if (!replaced) return null;

    result.style(component.style());
    replacement.addAll(children);

    return result.setChildren(replacement);
  }

  private void warnIfAdvancedChatDisabled() {
    if (!addon.configuration().incompatWarn().get()) return;
    if (addon.labyAPI().config().ingame().advancedChat().enabled().get()) return;

    addon.labyAPI().minecraft().chatExecutor().displayClientMessage(
        Component.translatable("betteremote.notifications.incompatWarn.advancedchat").color(NamedTextColor.RED)
    );
  }

  private static List<String> uncachedEmoteIds(String text) {
    List<String> ids = new ArrayList<>();
    for (String word : text.split(" ")) {
      Emote emote = Emote.parse(word);
      if (Objects.isNull(emote) || EmoteProvider.CACHED_EMOTES.containsKey(emote.id) || EmoteProvider.isFailed(emote.id)) continue;
      ids.add(emote.id);
    }
    return ids;
  }

  // Set while a held back message is shown again, so it isn't held back a second time
  private boolean redisplaying = false;

  @Subscribe
  public void onChatReceive(ChatReceiveEvent event) {
    if (!addon.configuration().enabled().get()) return;

    ChatMessage message = event.chatMessage();
    String plainText = message.getPlainText();
    List<String> unknownIds = redisplaying ? List.of() : uncachedEmoteIds(plainText);

    if (!unknownIds.isEmpty()) {
      // Editing a message that is already in chat doesn't work on newer LabyMod versions,
      // so the message is held back until its emotes are known and then shown again
      Component original = Objects.requireNonNullElse(message.originalComponent(), event.message());
      event.setCancelled(true);

      LOOKUP.execute(() -> {
        unknownIds.forEach(EmoteProvider::get);

        Task.builder(() -> {
          redisplaying = true;
          try {
            addon.labyAPI().minecraft().chatExecutor().displayClientMessage(original);
          } finally {
            redisplaying = false;
          }
        }).build().executeOnRenderThread();
      });
      return;
    }

    // Every emote is known, so they are inserted before the message is shown
    Component replacement = replaceEmote(event.message());
    if (Objects.isNull(replacement)) return;

    event.setMessage(replacement);
    warnIfAdvancedChatDisabled();
  }
}
