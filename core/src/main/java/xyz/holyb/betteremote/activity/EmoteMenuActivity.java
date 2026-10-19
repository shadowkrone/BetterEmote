package xyz.holyb.betteremote.activity;

import net.labymod.api.Textures.SpriteCommon;
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
import xyz.holyb.betteremote.emote.EmoteProvider;
import xyz.holyb.betteremote.site.WebsiteLogin;
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

  // The emote whose row shows the rename field, kept so it survives reloads
  private String renaming;

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

      ComponentWidget title = ComponentWidget.i18n("emote.menu.title").addId("menu-title");
      container.addContent(title);
    }

    this.initializeAddBar(container);

    this.statusWidget = ComponentWidget.component(this.status).addId("menu-status");
    container.addContent(this.statusWidget);

    Map<String, Emote> emotes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    emotes.putAll(this.addon.configuration().getEmotes());

    if (emotes.isEmpty()) {
      container.addFlexibleContent(ComponentWidget.i18n("emote.menu.empty").addId("menu-empty"));
    } else {
      VerticalListWidget<HorizontalListWidget> emotesList = new VerticalListWidget<HorizontalListWidget>().addId("menu-emotes-list");
      emotes.forEach((name, emote) -> emotesList.addChild(
          name.equals(this.renaming) ? this.renameRow(name) : this.emoteRow(name, emote)));

      container.addFlexibleContent(new ScrollWidget(emotesList).addId("menu-emotes-scroll"));
    }

    HorizontalListWidget buttons = new HorizontalListWidget().addId("menu-buttons");

    // Uploading and browsing emotes happens on the website, the button logs the player in there
    ButtonWidget websiteButton = ButtonWidget.i18n("emote.menu.website.button").addId("menu-website-button");
    websiteButton.setActionListener(() -> {
      websiteButton.setEnabled(false);
      WebsiteLogin.openWebsite(this::setStatus, () -> websiteButton.setEnabled(true));
    });
    buttons.addEntry(websiteButton);

    if (!this.fromSettings) {
      // Emotes uploaded on the website can be saved right here, without opening the browser
      ButtonWidget browseButton = ButtonWidget.i18n("emote.menu.browse").addId("menu-browse-button");
      browseButton.setActionListener(() -> this.addon.labyAPI().minecraft().minecraftWindow().displayScreen(new SiteEmotesActivity()));
      buttons.addEntry(browseButton);

      ButtonWidget settingsButton = ButtonWidget.i18n("emote.menu.settings").addId("menu-settings-button");
      settingsButton.setActionListener(this.addon::openSettings);
      buttons.addEntry(settingsButton);
    }
    container.addContent(buttons);

    this.initializeCodeRow(container);

    this.document().addChild(container);
  }

  // The menu is only for managing emotes, they are picked from the panel next to the chat input
  private HorizontalListWidget emoteRow(String name, Emote emote) {
    HorizontalListWidget row = new HorizontalListWidget().addId("menu-emote-row");
    row.addEntry(new EmoteWidget(name, emote));

    ButtonWidget renameButton = ButtonWidget.icon(SpriteCommon.EDIT).addId("menu-rename-button");
    renameButton.setHoverComponent(Component.translatable("emote.menu.rename.button"));
    renameButton.setActionListener(() -> {
      this.renaming = name;
      this.reload();
    });
    row.addEntry(renameButton);

    ButtonWidget removeButton = ButtonWidget.deleteButton().addId("menu-remove-button");
    removeButton.setActionListener(() -> this.removeEmote(name));
    row.addEntry(removeButton);

    return row;
  }

  private HorizontalListWidget renameRow(String name) {
    HorizontalListWidget row = new HorizontalListWidget().addId("menu-emote-row");

    TextFieldWidget nameInput = new TextFieldWidget().addId("menu-rename-input");
    nameInput.setText(name);
    nameInput.setFocused(true);
    row.addEntry(nameInput);

    Runnable confirm = () -> this.renameEmote(name, nameInput.getText().trim());
    nameInput.submitHandler(text -> confirm.run());

    // Text instead of icons, the check mark sprite shows up as an X on current LabyMod versions
    ButtonWidget confirmButton = ButtonWidget.i18n("emote.menu.rename.save").addId("menu-rename-confirm");
    confirmButton.setActionListener(confirm);
    row.addEntry(confirmButton);

    ButtonWidget cancelButton = ButtonWidget.i18n("emote.menu.rename.cancel").addId("menu-rename-cancel");
    cancelButton.setActionListener(() -> {
      this.renaming = null;
      this.reload();
    });
    row.addEntry(cancelButton);

    return row;
  }

  private void renameEmote(String oldName, String newName) {
    if (newName.equals(oldName)) {
      this.renaming = null;
      this.reload();
      return;
    }
    // Chat finds emotes by splitting at spaces, and "|" separates the name from the id
    if (newName.isEmpty() || newName.contains(" ") || newName.contains(EmoteProvider.EMOTE_SPLITTER)) {
      this.setStatus(Component.translatable("emote.menu.status.invalidName", NamedTextColor.RED));
      return;
    }
    if (!this.addon.renameEmote(oldName, newName)) {
      this.setStatus(Component.translatable("emote.menu.status.nameTaken", NamedTextColor.RED, Component.text(newName)));
      return;
    }

    this.renaming = null;
    this.status = Component.translatable("emote.menu.status.renamed", NamedTextColor.GREEN,
        Component.text(oldName), Component.text(newName));
    this.reload();
  }

  // Links this Minecraft account to the website account whose profile page shows the code
  private void initializeCodeRow(FlexibleContentWidget container) {
    HorizontalListWidget codeRow = new HorizontalListWidget().addId("menu-code-row");

    TextFieldWidget codeInput = new TextFieldWidget().addId("menu-code-input");
    codeInput.placeholder(Component.translatable("emote.menu.website.codeInput"));
    codeInput.maximalLength(9);
    codeRow.addEntry(codeInput);

    ButtonWidget codeButton = ButtonWidget.i18n("emote.menu.website.codeButton").addId("menu-code-button");
    Runnable submit = () -> {
      String code = codeInput.getText().trim();
      if (code.isEmpty()) return;
      codeButton.setEnabled(false);
      WebsiteLogin.enterCode(code, this::setStatus, () -> {
        codeButton.setEnabled(true);
        codeInput.setText("");
      });
    };
    codeButton.setActionListener(submit);
    codeInput.submitHandler(text -> submit.run());
    codeRow.addEntry(codeButton);

    container.addContent(codeRow);
  }

  private void removeEmote(String name) {
    this.addon.configuration().getEmotes().remove(name);
    this.addon.saveConfiguration();

    this.status = Component.translatable("emote.menu.status.removed", NamedTextColor.GREEN, Component.text(name));
    this.reload();
  }

  private void initializeAddBar(FlexibleContentWidget container) {
    HorizontalListWidget searchRow = new HorizontalListWidget().addId("menu-search-row");

    TextFieldWidget searchInput = new TextFieldWidget().addId("menu-search-input");
    searchInput.placeholder(Component.translatable("emote.menu.add.searchInput"));
    searchRow.addEntry(searchInput);

    ButtonWidget searchButton = ButtonWidget.i18n("emote.menu.add.searchButton").addId("menu-search-button");
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
    nameInput.placeholder(Component.translatable("emote.menu.add.nameInput"));
    addRow.addEntry(nameInput);

    ButtonWidget addButton = ButtonWidget.i18n("labymod.ui.button.add").addId("menu-add-button");
    addRow.addEntry(addButton);

    container.addContent(addRow);

    Runnable search = () -> {
      if (searchInput.getText().trim().length() < 3) {
        this.setStatus(Component.translatable("emote.menu.status.tooShort", NamedTextColor.RED));
        return;
      }

      this.setStatus(Component.translatable("emote.menu.status.searching", NamedTextColor.GRAY));
      BTTVSearch.search(searchInput.getText(), found -> Task.builder(() -> {
        results.clear();
        for (BTTVEmote emote : found) {
          results.add(emote);
        }

        if (found.isEmpty()) {
          this.setStatus(Component.translatable("emote.menu.status.noResults", NamedTextColor.RED));
          return;
        }

        results.setSelected(found.get(0));
        preview.icon().set(Icon.url(found.get(0).getImageURL(1)));
        this.setStatus(Component.translatable("emote.menu.status.found", NamedTextColor.GRAY,
            Component.text(found.size())));
      }).build().executeOnRenderThread());
    };
    searchButton.setActionListener(search);
    searchInput.submitHandler(text -> search.run());

    addButton.setActionListener(() -> {
      BTTVEmote selected = results.getSelected();
      if (Objects.isNull(selected)) {
        this.setStatus(Component.translatable("emote.menu.status.noSelection", NamedTextColor.RED));
        return;
      }

      // Without a name, the emote keeps its BTTV code
      String name = nameInput.getText().trim().isEmpty() ? selected.code : nameInput.getText().trim();
      if (name.contains(" ")) {
        this.setStatus(Component.translatable("emote.menu.status.invalidName", NamedTextColor.RED));
        return;
      }

      addButton.setEnabled(false);
      this.setStatus(Component.translatable("emote.menu.status.adding", NamedTextColor.GRAY, Component.text(name)));
      this.addon.addEmote(name, selected.id, serverEmote -> {
        if (Objects.isNull(serverEmote)) {
          addButton.setEnabled(true);
          this.setStatus(Component.translatable("emote.menu.status.rejected", NamedTextColor.RED, Component.text(name)));
          return;
        }

        this.status = Component.translatable("emote.menu.status.added", NamedTextColor.GREEN, Component.text(name));
        this.reload();
      });
    });
  }

  private void setStatus(Component status) {
    this.status = status;
    if (Objects.nonNull(this.statusWidget)) this.statusWidget.setComponent(status);
  }
}
