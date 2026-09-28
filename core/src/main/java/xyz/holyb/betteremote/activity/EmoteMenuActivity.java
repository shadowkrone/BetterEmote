package xyz.holyb.betteremote.activity;

import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.activity.Activity;
import net.labymod.api.client.gui.screen.activity.AutoActivity;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.TextFieldWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.ScrollWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.HorizontalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.VerticalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import net.labymod.api.util.concurrent.task.Task;
import xyz.holyb.betteremote.BetterEmoteAddon;
import xyz.holyb.betteremote.bttv.BTTVEmote;
import xyz.holyb.betteremote.bttv.BTTVSearch;
import xyz.holyb.betteremote.emote.Emote;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Menu with every saved emote, where the player can add new ones from the bar at
 * the top and remove existing ones. Opened ingame with the emote menu key and from
 * the addon settings.
 */
@AutoActivity
@Link("emotes.lss")
public class EmoteMenuActivity extends Activity {

  private final BetterEmoteAddon addon;
  private final boolean fromSettings;

  // Kept on the activity so the result of adding an emote survives the reload afterwards
  private Component status = Component.empty();
  private ComponentWidget statusWidget;

  public EmoteMenuActivity() {
    this(false);
  }

  /**
   * @param fromSettings true when opened from the addon settings, where the menu is laid out
   *                     as part of the settings page and doesn't lead out of it
   */
  public EmoteMenuActivity(boolean fromSettings) {
    this.addon = BetterEmoteAddon.get();
    this.fromSettings = fromSettings;
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);

    // In the settings the menu fills the settings page, which already has a background and a title
    FlexibleContentWidget container = new FlexibleContentWidget()
        .addId(this.fromSettings ? "settings-container" : "menu-container");

    if (!this.fromSettings) {
      // Darkens the game behind the menu, like the LabyMod settings do
      this.document().addChild(new DivWidget().addId("menu-background"));

      ComponentWidget title = ComponentWidget.i18n("betteremote.menu.title").addId("menu-title");
      container.addContent(title);
    }

    this.initializeAddBar(container);

    this.statusWidget = ComponentWidget.component(this.status).addId("menu-status");
    container.addContent(this.statusWidget);

    Map<String, Emote> emotes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    emotes.putAll(this.addon.configuration().getEmotes());

    if (emotes.isEmpty()) {
      container.addFlexibleContent(ComponentWidget.i18n("betteremote.menu.empty").addId("menu-empty"));
    } else {
      VerticalListWidget<HorizontalListWidget> emotesList = new VerticalListWidget<HorizontalListWidget>().addId("menu-emotes-list");
      String prefix = this.addon.configuration().prefix().get();

      emotes.forEach((name, emote) -> {
        HorizontalListWidget row = new HorizontalListWidget().addId("menu-emote-row");

        EmoteWidget emoteWidget = new EmoteWidget(name, emote);
        if (!this.fromSettings) {
          // Clicking an emote opens the chat with its code, ready to send
          emoteWidget.setActionListener(() -> this.addon.labyAPI().minecraft().openChat(prefix + name + prefix + " "));
        }
        row.addEntry(emoteWidget);

        ButtonWidget removeButton = ButtonWidget.deleteButton().addId("menu-remove-button");
        removeButton.setActionListener(() -> this.removeEmote(name));
        row.addEntry(removeButton);

        emotesList.addChild(row);
      });

      container.addFlexibleContent(new ScrollWidget(emotesList).addId("menu-emotes-scroll"));
    }

    if (!this.fromSettings) {
      ButtonWidget settingsButton = ButtonWidget.i18n("betteremote.menu.settings").addId("menu-settings-button");
      settingsButton.setActionListener(this.addon::openSettings);
      container.addContent(settingsButton);
    }

    this.document().addChild(container);
  }

  private void removeEmote(String name) {
    this.addon.configuration().getEmotes().remove(name);
    this.addon.saveConfiguration();

    this.status = Component.translatable("betteremote.menu.status.removed", NamedTextColor.GREEN, Component.text(name));
    this.reload();
  }

  private void initializeAddBar(FlexibleContentWidget container) {
    HorizontalListWidget searchRow = new HorizontalListWidget().addId("menu-search-row");

    TextFieldWidget searchInput = new TextFieldWidget().addId("menu-search-input");
    searchInput.placeholder(Component.translatable("betteremote.menu.add.searchInput"));
    searchRow.addEntry(searchInput);

    ButtonWidget searchButton = ButtonWidget.i18n("betteremote.menu.add.searchButton").addId("menu-search-button");
    searchRow.addEntry(searchButton);

    container.addContent(searchRow);

    HorizontalListWidget addRow = new HorizontalListWidget().addId("menu-add-row");

    IconWidget preview = new IconWidget(Icon.head(UUID.fromString("57e13c39-5755-4354-aefa-b60195ff6f27"))).addId("menu-preview");
    addRow.addEntry(preview);

    DropdownWidget<BTTVEmote> results = new DropdownWidget<BTTVEmote>().addId("menu-results");
    results.setEntryRenderer(BTTVEmote.entryRenderer());
    results.setChangeListener(emote -> preview.icon().set(Icon.url(emote.getImageURL(1))));
    addRow.addEntry(results);

    TextFieldWidget nameInput = new TextFieldWidget().addId("menu-name-input");
    nameInput.placeholder(Component.translatable("betteremote.menu.add.nameInput"));
    addRow.addEntry(nameInput);

    ButtonWidget addButton = ButtonWidget.i18n("labymod.ui.button.add").addId("menu-add-button");
    addRow.addEntry(addButton);

    container.addContent(addRow);

    Runnable search = () -> {
      if (searchInput.getText().trim().length() < 3) {
        this.setStatus(Component.translatable("betteremote.menu.status.tooShort", NamedTextColor.RED));
        return;
      }

      this.setStatus(Component.translatable("betteremote.menu.status.searching", NamedTextColor.GRAY));
      BTTVSearch.search(searchInput.getText(), found -> Task.builder(() -> {
        results.clear();
        for (BTTVEmote emote : found) {
          results.add(emote);
        }

        if (found.isEmpty()) {
          this.setStatus(Component.translatable("betteremote.menu.status.noResults", NamedTextColor.RED));
          return;
        }

        results.setSelected(found.get(0));
        preview.icon().set(Icon.url(found.get(0).getImageURL(1)));
        this.setStatus(Component.translatable("betteremote.menu.status.found", NamedTextColor.GRAY,
            Component.text(found.size())));
      }).build().executeOnRenderThread());
    };
    searchButton.setActionListener(search);
    searchInput.submitHandler(text -> search.run());

    addButton.setActionListener(() -> {
      BTTVEmote selected = results.getSelected();
      if (Objects.isNull(selected)) {
        this.setStatus(Component.translatable("betteremote.menu.status.noSelection", NamedTextColor.RED));
        return;
      }

      // Without a name, the emote keeps its BTTV code
      String name = nameInput.getText().trim().isEmpty() ? selected.code : nameInput.getText().trim();
      if (name.contains(" ")) {
        this.setStatus(Component.translatable("betteremote.menu.status.invalidName", NamedTextColor.RED));
        return;
      }

      addButton.setEnabled(false);
      this.setStatus(Component.translatable("betteremote.menu.status.adding", NamedTextColor.GRAY, Component.text(name)));
      this.addon.addEmote(name, selected.id, serverEmote -> {
        if (Objects.isNull(serverEmote)) {
          addButton.setEnabled(true);
          this.setStatus(Component.translatable("betteremote.menu.status.rejected", NamedTextColor.RED, Component.text(name)));
          return;
        }

        this.status = Component.translatable("betteremote.menu.status.added", NamedTextColor.GREEN, Component.text(name));
        this.reload();
      });
    });
  }

  private void setStatus(Component status) {
    this.status = status;
    if (Objects.nonNull(this.statusWidget)) this.statusWidget.setComponent(status);
  }
}
