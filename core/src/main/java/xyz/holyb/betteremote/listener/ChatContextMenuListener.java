package xyz.holyb.betteremote.listener;

import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.component.serializer.plain.PlainTextComponentSerializer;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.screen.widget.context.ContextMenuEntry;
import net.labymod.api.configuration.labymod.chat.AdvancedChatMessage;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.chat.advanced.AdvancedChatMessageContextMenuEvent;
import xyz.holyb.betteremote.BetterEmoteAddon;
import xyz.holyb.betteremote.emote.Emote;
import xyz.holyb.betteremote.emote.EmoteProvider;
import java.util.List;
import java.util.Objects;

/**
 * Adds "Save" and "Use in chat" for every emote in a message to the right-click menu of
 * Advanced Chat, so players can take emotes other players sent.
 */
public class ChatContextMenuListener {
  // Long messages full of emotes would otherwise make the menu taller than the screen
  private static final int MAX_EMOTES = 5;

  private final BetterEmoteAddon addon;

  public ChatContextMenuListener(BetterEmoteAddon addon) {
    this.addon = addon;
  }

  @Subscribe
  public void onContextMenu(AdvancedChatMessageContextMenuEvent event) {
    if (!this.addon.configuration().enabled().get()) return;

    List<Emote> emotes = EmoteProvider.findCached(originalText(event.message()));
    if (emotes.isEmpty()) return;

    event.addEntry(ContextMenuEntry.divider());
    for (Emote emote : emotes.subList(0, Math.min(emotes.size(), MAX_EMOTES))) {
      event.addEntry(this.saveEntry(emote));
      event.addEntry(this.useEntry(emote));
    }
  }

  // The shown component has images instead of emote codes, so the codes are read from the original
  private static String originalText(AdvancedChatMessage message) {
    if (Objects.nonNull(message.chatMessage())) return message.chatMessage().getOriginalPlainText();
    Component original = Objects.requireNonNullElse(message.originalComponent(), message.component());
    return PlainTextComponentSerializer.plainText().serialize(original);
  }

  private ContextMenuEntry saveEntry(Emote emote) {
    String saved = this.addon.savedName(emote);
    Icon icon = Icon.url(emote.getStaticImageURL(1));

    if (Objects.nonNull(saved)) {
      return ContextMenuEntry.builder()
          .icon(icon)
          .text(Component.translatable("emote.chat.alreadySaved", Component.text(saved)))
          .disabled()
          .build();
    }

    return ContextMenuEntry.builder()
        .icon(icon)
        .text(Component.translatable("emote.chat.save", Component.text(emote.name())))
        .simpleClickHandler(entry -> {
          String name = this.addon.saveEmote(emote);
          String prefix = this.addon.configuration().prefix().get();
          this.addon.labyAPI().minecraft().chatExecutor().displayClientMessage(
              Component.translatable("emote.chat.saved", NamedTextColor.GREEN, Component.text(prefix + name + prefix))
          );
        })
        .build();
  }

  private ContextMenuEntry useEntry(Emote emote) {
    return ContextMenuEntry.builder()
        .text(Component.translatable("emote.chat.use", Component.text(emote.name())))
        // The code works without saving the emote, everyone with the addon sees it
        .simpleClickHandler(entry -> this.addon.labyAPI().minecraft().chatExecutor().insertText(emote.id + " "))
        .build();
  }
}
