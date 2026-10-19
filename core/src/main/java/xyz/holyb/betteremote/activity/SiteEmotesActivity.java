package xyz.holyb.betteremote.activity;

import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.activity.Activity;
import net.labymod.api.client.gui.screen.activity.AutoActivity;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.TextFieldWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.renderer.DefaultEntryRenderer;
import net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.ScrollWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.HorizontalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.VerticalListWidget;
import net.labymod.api.util.concurrent.task.Task;
import xyz.holyb.betteremote.BetterEmoteAddon;
import xyz.holyb.betteremote.emote.Emote;
import xyz.holyb.betteremote.emote.EmoteProvider;
import xyz.holyb.betteremote.site.SiteEmote;
import xyz.holyb.betteremote.site.SiteEmoteSearch;
import xyz.holyb.betteremote.site.SiteEmoteSearch.Filter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Lists every emote uploaded on the website, so players can save them without leaving the game.
 * Opened from the emote menu.
 */
@AutoActivity
@Link("emotes.lss")
public class SiteEmotesActivity extends Activity {

  private final BetterEmoteAddon addon;

  // Kept on the activity, so the list survives the reload after a search
  private final List<SiteEmote> emotes = new ArrayList<>();
  private String query = "";
  private Filter filter = Filter.ALL;
  private boolean loading;
  private boolean hasMore;

  private Component status = Component.empty();
  private ComponentWidget statusWidget;

  public SiteEmotesActivity() {
    this.addon = BetterEmoteAddon.get();
    this.load(false);
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);

    this.document().addChild(new DivWidget().addId("menu-background"));

    FlexibleContentWidget container = new FlexibleContentWidget().addId("menu-container");
    container.addContent(ComponentWidget.i18n("emote.browse.title").addId("menu-title"));

    HorizontalListWidget searchRow = new HorizontalListWidget().addId("browse-search-row");

    TextFieldWidget searchInput = new TextFieldWidget().addId("browse-search-input");
    searchInput.placeholder(Component.translatable("emote.browse.searchInput"));
    searchInput.setText(this.query);
    searchInput.submitHandler(text -> this.search(text));
    searchRow.addEntry(searchInput);

    DropdownWidget<Filter> filterDropdown = new DropdownWidget<Filter>().addId("browse-filter");
    DefaultEntryRenderer<Filter> filterRenderer = new DefaultEntryRenderer<>();
    filterRenderer.setDisplayNameProvider(filter -> Component.translatable(filter.translationKey()));
    filterDropdown.setEntryRenderer(filterRenderer);
    filterDropdown.addAll(Filter.values());
    filterDropdown.setSelected(this.filter);
    filterDropdown.setChangeListener(filter -> {
      this.filter = filter;
      this.search(searchInput.getText());
    });
    searchRow.addEntry(filterDropdown);

    ButtonWidget searchButton = ButtonWidget.i18n("emote.menu.add.searchButton").addId("browse-search-button");
    searchButton.setActionListener(() -> this.search(searchInput.getText()));
    searchRow.addEntry(searchButton);

    container.addContent(searchRow);

    this.statusWidget = ComponentWidget.component(this.status).addId("menu-status");
    container.addContent(this.statusWidget);

    if (this.emotes.isEmpty()) {
      if (!this.loading) container.addFlexibleContent(ComponentWidget.i18n("emote.browse.empty").addId("menu-empty"));
    } else {
      VerticalListWidget<HorizontalListWidget> emotesList = new VerticalListWidget<HorizontalListWidget>().addId("menu-emotes-list");
      for (SiteEmote emote : this.emotes) {
        emotesList.addChild(this.emoteRow(emote));
      }

      if (this.hasMore) {
        HorizontalListWidget moreRow = new HorizontalListWidget().addId("browse-more-row");
        ButtonWidget moreButton = ButtonWidget.i18n("emote.browse.loadMore").addId("browse-more-button");
        moreButton.setActionListener(() -> {
          moreButton.setEnabled(false);
          this.load(true);
        });
        moreRow.addEntry(moreButton);
        emotesList.addChild(moreRow);
      }

      container.addFlexibleContent(new ScrollWidget(emotesList).addId("menu-emotes-scroll"));
    }

    HorizontalListWidget buttons = new HorizontalListWidget().addId("menu-buttons");
    ButtonWidget backButton = ButtonWidget.i18n("emote.browse.back").addId("menu-back-button");
    backButton.setActionListener(() -> this.addon.labyAPI().minecraft().minecraftWindow().displayScreen(new EmoteMenuActivity()));
    buttons.addEntry(backButton);
    container.addContent(buttons);

    this.document().addChild(container);
  }

  private HorizontalListWidget emoteRow(SiteEmote emote) {
    HorizontalListWidget row = new HorizontalListWidget().addId("browse-emote-row");
    row.addEntry(new EmoteWidget(emote.code, emote));

    String saved = this.addon.savedName(emote);
    ButtonWidget saveButton = Objects.nonNull(saved)
        ? ButtonWidget.i18n("emote.browse.saved")
        : ButtonWidget.i18n("emote.browse.save");
    saveButton.addId("browse-save-button");
    saveButton.setEnabled(Objects.isNull(saved));
    saveButton.setActionListener(() -> {
      Emote plain = emote.toEmote();
      EmoteProvider.CACHED_EMOTES.put(plain.id, plain);
      String name = this.addon.saveEmote(plain);

      String prefix = this.addon.configuration().prefix().get();
      this.status = Component.translatable("emote.chat.saved", NamedTextColor.GREEN, Component.text(prefix + name + prefix));
      this.reload();
    });
    row.addEntry(saveButton);

    return row;
  }

  private void search(String query) {
    this.query = query;
    this.load(false);
  }

  /** Fetches the first page, or the next one when more is true, and shows it once it arrives. */
  private void load(boolean more) {
    if (this.loading) return;
    this.loading = true;

    int offset = more ? this.emotes.size() : 0;
    this.setStatus(Component.translatable("emote.browse.loading", NamedTextColor.GRAY));
    SiteEmoteSearch.search(this.query, this.filter, offset, found -> Task.builder(() -> {
      this.loading = false;
      if (Objects.isNull(found)) {
        this.status = Component.translatable("emote.menu.website.unreachable", NamedTextColor.RED);
        this.reload();
        return;
      }

      if (!more) this.emotes.clear();
      this.emotes.addAll(found);
      this.hasMore = found.size() >= SiteEmoteSearch.PAGE_SIZE;

      this.status = this.emotes.isEmpty()
          ? Component.translatable("emote.menu.status.noResults", NamedTextColor.RED)
          : Component.translatable("emote.menu.status.found", NamedTextColor.GRAY, Component.text(this.emotes.size()));
      this.reload();
    }).build().executeOnRenderThread());
  }

  private void setStatus(Component status) {
    this.status = status;
    if (Objects.nonNull(this.statusWidget)) this.statusWidget.setComponent(status);
  }
}
