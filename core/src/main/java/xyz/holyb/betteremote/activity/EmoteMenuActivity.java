package xyz.holyb.betteremote.activity;

import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.component.format.TextColor;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.icon.catalog.IconsScreenshot;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.activity.Activity;
import net.labymod.api.client.gui.screen.activity.AutoActivity;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.widget.Widget;
import net.labymod.api.client.gui.screen.widget.action.ListSession;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.TextFieldWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.ScrollWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.HorizontalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.VerticalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.util.concurrent.task.Task;
import xyz.holyb.betteremote.BetterEmoteAddon;
import xyz.holyb.betteremote.bttv.BTTVEmote;
import xyz.holyb.betteremote.bttv.BTTVSearch;
import xyz.holyb.betteremote.emote.Emote;
import xyz.holyb.betteremote.emote.EmoteProvider;
import xyz.holyb.betteremote.site.SiteEmote;
import xyz.holyb.betteremote.site.SiteEmoteSearch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * The emote menu, with tabs: Search finds emotes on BetterTTV, Saved lists the saved emotes
 * (the ones in the chat menu) to rename and remove them, and BetterEmote lists the emotes uploaded
 * on the website. Search results are cards with a Save button. Opened ingame with the emote menu
 * key and from the emote panel next to the chat input.
 */
@AutoActivity
@Link("emotes.lss")
public class EmoteMenuActivity extends Activity {

  private enum Tab {
    SEARCH("emote.menu.tab.search"),
    SAVED("emote.menu.tab.saved"),
    BETTEREMOTE("emote.menu.tab.betteremote");

    private final String translationKey;

    Tab(String translationKey) {
      this.translationKey = translationKey;
    }
  }

  // Card size in GUI pixels, see .menu-card in emotes.lss. Rows hold as many cards as fit
  private static final int CARD_WIDTH = 84;
  private static final int CARD_SPACING = 4;
  // Longer names are cut on the card, hovering the emote shows the whole name
  private static final int CARD_NAME_LENGTH = 13;
  private static final Icon LOGO = Icon.texture(ResourceLocation.create("emote", "textures/icon.png"));

  private final BetterEmoteAddon addon;

  // Everything below is kept on the activity, so it survives the reloads after searching and saving
  private Tab tab = Tab.SEARCH;
  // The saved emote whose row shows the rename field
  private String renaming;

  // Empty until the player searches, then random trending emotes are shown
  private String bttvQuery = "";
  private final List<BTTVEmote> bttvResults = new ArrayList<>();
  private boolean bttvHasMore;
  private boolean bttvLoading;
  private boolean bttvLoaded;

  private String siteQuery = "";
  private final List<SiteEmote> siteResults = new ArrayList<>();
  private boolean siteHasMore;
  private boolean siteLoading;
  private boolean siteLoaded;
  private SiteEmoteSearch.Filter siteFilter = SiteEmoteSearch.Filter.ALL;

  // The card being saved right now, "bttv:<id>" or "site:<id>"
  private String saving;
  private int cardsPerRow = 6;
  // Keeps the scroll position when the menu reloads
  private final ListSession<Widget> listSession = new ListSession<>();

  private Component status = Component.empty();
  private ComponentWidget statusWidget;

  public EmoteMenuActivity() {
    this.addon = BetterEmoteAddon.get();
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);

    // The menu is 75% of the screen with 10 padding on each side, the scrollbar takes 10 more
    float width = parent.bounds().getWidth() * 0.75F - 20;
    this.cardsPerRow = Math.max(2, (int) ((width - 10 + CARD_SPACING) / (CARD_WIDTH + CARD_SPACING)));

    // Darkens the game behind the menu, like the LabyMod settings do
    this.document().addChild(new DivWidget().addId("menu-background"));
    FlexibleContentWidget container = new FlexibleContentWidget().addId("menu-container");

    // Logo and title in the middle, how many emotes are saved on the right
    DivWidget header = new DivWidget().addId("menu-header");
    HorizontalListWidget brand = new HorizontalListWidget().addId("menu-brand");
    brand.addEntry(new IconWidget(LOGO).addId("menu-logo"));
    brand.addEntry(ComponentWidget.i18n("emote.menu.title").addId("menu-title"));
    header.addChild(brand);
    header.addChild(ComponentWidget.component(Component.translatable("emote.menu.savedCount", NamedTextColor.GRAY,
        Component.text(this.addon.configuration().getEmotes().size()))).addId("menu-count"));
    container.addContent(header);

    container.addContent(this.tabs());
    if (this.tab == Tab.SEARCH) this.initializeBttvSearch(container);
    if (this.tab == Tab.BETTEREMOTE) this.initializeSiteSearch(container);

    this.statusWidget = ComponentWidget.component(this.status).addId("menu-status");
    container.addContent(this.statusWidget);

    VerticalListWidget<Widget> list = new VerticalListWidget<>(this.listSession).addId("menu-emotes-list");
    switch (this.tab) {
      case SEARCH -> this.addBttvResults(list);
      case SAVED -> this.addSaved(list);
      case BETTEREMOTE -> this.addSiteResults(list);
    }
    container.addFlexibleContent(new ScrollWidget(list).addId("menu-emotes-scroll"));

    String hint = switch (this.tab) {
      case SEARCH -> "emote.menu.hint.search";
      case SAVED -> "emote.menu.hint.saved";
      case BETTEREMOTE -> "emote.menu.hint.betteremote";
    };
    container.addContent(ComponentWidget.component(Component.translatable(hint, NamedTextColor.DARK_GRAY)).addId("menu-hint"));

    this.document().addChild(container);

    // Random BTTV emotes and the website's emotes are listed as soon as their tab is opened
    if (this.tab == Tab.SEARCH && !this.bttvLoaded) this.loadBttv(false);
    if (this.tab == Tab.BETTEREMOTE && !this.siteLoaded) this.loadSite(false);
  }

  /** Search, Saved and BetterEmote. */
  private HorizontalListWidget tabs() {
    HorizontalListWidget tabs = new HorizontalListWidget().addId("menu-tabs");
    for (Tab option : Tab.values()) {
      // The open tab is written in green, buttons don't take a background color
      TextColor color = option == this.tab ? NamedTextColor.GREEN : NamedTextColor.GRAY;
      ButtonWidget button = ButtonWidget.component(Component.translatable(option.translationKey, color)).addId("menu-tab");
      button.setActionListener(() -> {
        if (this.tab == option) return;
        this.tab = option;
        this.renaming = null;
        this.status = Component.empty();
        this.listSession.setScrollPositionY(0);
        this.reload();
      });
      tabs.addEntry(button);
    }
    return tabs;
  }

  // ---- Search (BetterTTV) ---------------------------------------------------------------------

  private void initializeBttvSearch(FlexibleContentWidget container) {
    TextFieldWidget input = new TextFieldWidget().addId("menu-search-input");
    input.placeholder(Component.translatable("emote.menu.add.searchInput"));
    input.setText(this.bttvQuery);
    // An empty search shows random emotes again
    input.submitHandler(text -> {
      String query = text.trim();
      if (!query.isEmpty() && query.length() < 3) {
        this.setStatus(Component.translatable("emote.menu.status.tooShort", NamedTextColor.RED));
        return;
      }
      this.bttvQuery = query;
      this.loadBttv(false);
    });
    container.addContent(input);
  }

  private void addBttvResults(VerticalListWidget<Widget> list) {
    if (this.bttvResults.isEmpty()) {
      if (this.bttvLoaded && !this.bttvLoading) list.addChild(ComponentWidget.i18n("emote.menu.status.noResults").addId("menu-empty"));
      return;
    }

    Component label = this.bttvQuery.isEmpty()
        ? Component.translatable("emote.menu.section.random", NamedTextColor.WHITE)
        : Component.translatable("emote.menu.section.results", NamedTextColor.WHITE,
            Component.text(this.bttvQuery), Component.text(this.bttvResults.size() + (this.bttvHasMore ? "+" : "")));
    list.addChild(ComponentWidget.component(label).addId("menu-section-label"));

    List<DivWidget> cards = new ArrayList<>();
    for (BTTVEmote emote : this.bttvResults) {
      cards.add(this.card(emote.icon(2), emote.code, emote.displayName(), this.isSaved(emote), "bttv:" + emote.id,
          () -> this.saveBttv(emote)));
    }
    this.addCards(list, cards);
    if (this.bttvHasMore) list.addChild(this.moreButton(this.bttvLoading, () -> this.loadBttv(true)));
  }

  /**
   * Fetches the first page of results for the query, or the next one when more is true.
   * Without a query it fetches random trending emotes instead.
   */
  private void loadBttv(boolean more) {
    if (this.bttvLoading) return;
    this.bttvLoading = true;

    String query = this.bttvQuery;
    this.setStatus(Component.translatable(query.isEmpty() ? "emote.browse.loading" : "emote.menu.status.searching", NamedTextColor.GRAY));
    Consumer<List<BTTVEmote>> callback = found -> Task.builder(() -> {
      this.bttvLoading = false;
      this.bttvLoaded = true;
      // A newer search was started while this one was running
      if (!query.equals(this.bttvQuery)) return;

      if (!more) {
        this.bttvResults.clear();
        this.listSession.setScrollPositionY(0);
      }
      this.bttvResults.addAll(found);
      // The random emotes are a single page
      this.bttvHasMore = !query.isEmpty() && found.size() >= BTTVSearch.PAGE_SIZE;
      this.status = this.bttvResults.isEmpty()
          ? Component.translatable("emote.menu.status.noResults", NamedTextColor.RED)
          : Component.empty();
      this.reload();
    }).build().executeOnRenderThread();

    if (query.isEmpty()) {
      BTTVSearch.random(callback);
    } else {
      BTTVSearch.search(query, more ? this.bttvResults.size() : 0, callback);
    }
  }

  // Our server stores BTTV emotes with their BTTV id as provider id
  private boolean isSaved(BTTVEmote emote) {
    return this.addon.configuration().getEmotes().values().stream().anyMatch(saved ->
        Objects.nonNull(saved) && Objects.nonNull(saved.provider)
            && "BTTV".equals(saved.provider.provider) && emote.id.equals(saved.provider.id));
  }

  /** Saves a result under its BTTV name, with a number added when that name is taken. */
  private void saveBttv(BTTVEmote emote) {
    if (Objects.nonNull(this.saving)) return;
    this.saving = "bttv:" + emote.id;
    this.reload();

    String name = this.addon.uniqueName(emote.code);
    this.addon.addEmote(name, emote.id, serverEmote -> {
      this.saving = null;
      this.status = Objects.isNull(serverEmote)
          ? Component.translatable("emote.menu.status.rejected", NamedTextColor.RED, Component.text(name))
          : Component.translatable("emote.menu.status.added", NamedTextColor.GREEN, Component.text(name));
      this.reload();
    });
  }

  // ---- Saved ---------------------------------------------------------------------------------

  /** The saved emotes, the same ones the chat menu shows, with rename and remove. */
  private void addSaved(VerticalListWidget<Widget> list) {
    Map<String, Emote> emotes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    emotes.putAll(this.addon.configuration().getEmotes());

    if (emotes.isEmpty()) {
      list.addChild(ComponentWidget.i18n("emote.menu.empty").addId("menu-empty"));
      return;
    }
    emotes.forEach((name, emote) -> list.addChild(name.equals(this.renaming) ? this.renameRow(name) : this.emoteRow(name, emote)));
  }

  private HorizontalListWidget emoteRow(String name, Emote emote) {
    HorizontalListWidget row = new HorizontalListWidget().addId("menu-emote-row");
    row.addEntry(new EmoteWidget(name, emote));

    ButtonWidget renameButton = ButtonWidget.icon(IconsScreenshot.EDIT).addId("menu-rename-button");
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

  private void removeEmote(String name) {
    this.addon.configuration().getEmotes().remove(name);
    this.addon.saveConfiguration();

    this.status = Component.translatable("emote.menu.status.removed", NamedTextColor.GREEN, Component.text(name));
    this.reload();
  }

  // ---- BetterEmote (the website) --------------------------------------------------------------

  private void initializeSiteSearch(FlexibleContentWidget container) {
    TextFieldWidget input = new TextFieldWidget().addId("menu-search-input");
    input.placeholder(Component.translatable("emote.browse.searchInput"));
    input.setText(this.siteQuery);
    // An empty search lists every emote again
    input.submitHandler(text -> {
      this.siteQuery = text.trim();
      this.loadSite(false);
    });
    container.addContent(input);

    HorizontalListWidget filters = new HorizontalListWidget().addId("menu-filter-row");
    for (SiteEmoteSearch.Filter option : SiteEmoteSearch.Filter.values()) {
      TextColor color = option == this.siteFilter ? NamedTextColor.GREEN : NamedTextColor.GRAY;
      ButtonWidget button = ButtonWidget.component(Component.translatable(option.translationKey(), color)).addId("menu-filter-wide");
      button.setActionListener(() -> {
        if (this.siteFilter == option) return;
        this.siteFilter = option;
        this.loadSite(false);
      });
      filters.addEntry(button);
    }
    container.addContent(filters);
  }

  private void addSiteResults(VerticalListWidget<Widget> list) {
    if (this.siteResults.isEmpty()) {
      if (this.siteLoaded && !this.siteLoading) list.addChild(ComponentWidget.i18n("emote.browse.empty").addId("menu-empty"));
      return;
    }

    List<DivWidget> cards = new ArrayList<>();
    for (SiteEmote emote : this.siteResults) {
      Component hover = Component.text(emote.code).append(Component.text("\n")).append(emote.description());
      cards.add(this.card(emote.icon(2), emote.code, hover, Objects.nonNull(this.addon.savedName(emote)), "site:" + emote.id,
          () -> this.saveSite(emote)));
    }
    this.addCards(list, cards);
    if (this.siteHasMore) list.addChild(this.moreButton(this.siteLoading, () -> this.loadSite(true)));
  }

  /** Fetches the first page of the website's emotes, or the next one when more is true. */
  private void loadSite(boolean more) {
    if (this.siteLoading) return;
    this.siteLoading = true;

    String query = this.siteQuery;
    SiteEmoteSearch.Filter filter = this.siteFilter;
    this.setStatus(Component.translatable("emote.browse.loading", NamedTextColor.GRAY));
    SiteEmoteSearch.search(query, filter, more ? this.siteResults.size() : 0, found -> Task.builder(() -> {
      this.siteLoading = false;
      this.siteLoaded = true;
      if (Objects.isNull(found)) {
        this.status = Component.translatable("emote.menu.website.unreachable", NamedTextColor.RED);
        this.reload();
        return;
      }
      // The search or filter changed while this page was loading
      if (!query.equals(this.siteQuery) || filter != this.siteFilter) return;

      if (!more) {
        this.siteResults.clear();
        this.listSession.setScrollPositionY(0);
      }
      this.siteResults.addAll(found);
      this.siteHasMore = found.size() >= SiteEmoteSearch.PAGE_SIZE;
      this.status = this.siteResults.isEmpty()
          ? Component.translatable("emote.menu.status.noResults", NamedTextColor.RED)
          : Component.empty();
      this.reload();
    }).build().executeOnRenderThread());
  }

  /** Saves a website emote under its own name, with a number added when that name is taken. */
  private void saveSite(SiteEmote emote) {
    Emote plain = emote.toEmote();
    EmoteProvider.CACHED_EMOTES.put(plain.id, plain);
    String name = this.addon.saveEmote(plain);

    String prefix = this.addon.configuration().prefix().get();
    this.status = Component.translatable("emote.chat.saved", NamedTextColor.GREEN, Component.text(prefix + name + prefix));
    this.reload();
  }

  // ---- Cards ----------------------------------------------------------------------------------

  /**
   * One emote card: the emote, its name, and Save, or Saved when the player already has it.
   * A div with fixed positions, lists would stretch the emote to their full width.
   */
  private DivWidget card(Icon icon, String name, Component hover, boolean saved, String key, Runnable save) {
    DivWidget card = new DivWidget().addId("menu-card");

    IconWidget iconWidget = new IconWidget(icon).addId("menu-card-emote");
    iconWidget.setHoverComponent(hover);
    card.addChild(iconWidget);

    String shortName = name.length() > CARD_NAME_LENGTH ? name.substring(0, CARD_NAME_LENGTH - 1) + "…" : name;
    card.addChild(ComponentWidget.text(shortName).addId("menu-card-name"));

    boolean saving = key.equals(this.saving);
    ButtonWidget button = ButtonWidget.i18n(saved ? "emote.menu.add.saved" : saving ? "emote.menu.add.saving" : "emote.menu.add.save")
        .addId("menu-card-save");
    button.setEnabled(!saved && Objects.isNull(this.saving));
    button.setActionListener(save);
    card.addChild(button);
    return card;
  }

  /** Puts the cards in rows of as many as fit in the menu. */
  private void addCards(VerticalListWidget<Widget> list, List<DivWidget> cards) {
    HorizontalListWidget row = null;
    for (int i = 0; i < cards.size(); i++) {
      if (i % this.cardsPerRow == 0) {
        row = new HorizontalListWidget().addId("menu-card-row");
        list.addChild(row);
      }
      row.addEntry(cards.get(i));
    }
  }

  private HorizontalListWidget moreButton(boolean loading, Runnable load) {
    HorizontalListWidget row = new HorizontalListWidget().addId("menu-more-row");
    ButtonWidget button = ButtonWidget.i18n("emote.browse.loadMore").addId("menu-more-button");
    button.setEnabled(!loading);
    button.setActionListener(() -> {
      button.setEnabled(false);
      load.run();
    });
    row.addEntry(button);
    return row;
  }

  private void setStatus(Component status) {
    this.status = status;
    if (Objects.nonNull(this.statusWidget)) this.statusWidget.setComponent(status);
  }
}
