package xyz.holyb.betteremote.activity;

import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.activity.AutoActivity;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.activity.types.chatinput.ChatInputTabActivity;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.ScrollWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.TilesGridWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.VerticalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import xyz.holyb.betteremote.BetterEmoteAddon;
import xyz.holyb.betteremote.emote.Emote;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Panel opened from the emote button next to the chat input, like the LabyMod symbols.
 * Clicking an emote types its code into the chat input.
 */
@AutoActivity
@Link("emotes.lss")
public class EmoteChatTabActivity extends ChatInputTabActivity<FlexibleContentWidget> {

  private final BetterEmoteAddon addon;

  public EmoteChatTabActivity() {
    this.addon = BetterEmoteAddon.get();
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);

    this.contentWidget = new FlexibleContentWidget().addId("chat-tab-container");

    Map<String, Emote> emotes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    emotes.putAll(this.addon.configuration().getEmotes());

    if (emotes.isEmpty()) {
      this.contentWidget.addFlexibleContent(ComponentWidget.i18n("emote.menu.empty").addId("chat-tab-empty"));
    } else {
      TilesGridWidget<IconWidget> grid = new TilesGridWidget<IconWidget>().addId("chat-tab-grid");
      String prefix = this.addon.configuration().prefix().get();

      emotes.forEach((name, emote) -> {
        if (Objects.isNull(emote) || Objects.isNull(emote.id)) return;

        IconWidget emoteWidget = new IconWidget(emote.icon(1)).addId("chat-tab-emote");
        emoteWidget.setHoverComponent(Component.text(name));
        // Keeps whatever the player already typed and adds the emote at the cursor. Clicks only
        // reach setPressable, setActionListener is only called by buttons
        emoteWidget.setPressable(() -> this.addon.labyAPI().minecraft().chatExecutor().insertText(prefix + name + prefix + " "));
        grid.addTile(emoteWidget);
      });

      // ScrollWidget only takes lists, so the grid is wrapped in one
      VerticalListWidget<TilesGridWidget<IconWidget>> list = new VerticalListWidget<TilesGridWidget<IconWidget>>().addId("chat-tab-list");
      list.addChild(grid);
      this.contentWidget.addFlexibleContent(new ScrollWidget(list).addId("chat-tab-scroll"));
    }

    // Adding and removing emotes happens in the full menu
    ButtonWidget menuButton = ButtonWidget.i18n("emote.chatTab.openMenu").addId("chat-tab-menu-button");
    menuButton.setActionListener(() -> this.addon.labyAPI().minecraft().minecraftWindow().displayScreen(new EmoteMenuActivity()));
    this.contentWidget.addContent(menuButton);

    this.document().addChild(this.contentWidget);
  }
}
