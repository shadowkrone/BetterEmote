package xyz.holyb.emotechat.activity;

import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.activity.Activity;
import net.labymod.api.client.gui.screen.activity.AutoActivity;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.widget.Widget;
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
import xyz.holyb.emotechat.EmoteChatAddon;
import xyz.holyb.emotechat.bttv.BTTVEmote;
import xyz.holyb.emotechat.bttv.BTTVSearch;
import xyz.holyb.emotechat.emote.Emote;
import xyz.holyb.emotechat.emote.EmoteProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@AutoActivity
@Link("emotes.lss")
public class EmotesActivity extends Activity {

  private final EmoteChatAddon addon;

  private Action action;

  private ButtonWidget removeButton;
  private final VerticalListWidget<EmoteWidget> emotesList;
  private final Map<String, EmoteWidget> emoteWidgets;

  public EmotesActivity() {
    this.addon = EmoteChatAddon.get();

    this.emoteWidgets = new HashMap<>();
    this.addon.configuration().getEmotes().forEach((name, emote) -> this.emoteWidgets.put(name, new EmoteWidget(name, emote)));

    this.emotesList = new VerticalListWidget<>().addId("emotes-list");
    this.emotesList.setSelectCallback(emoteWidget -> this.removeButton.setEnabled(true));
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);

    FlexibleContentWidget container = new FlexibleContentWidget()
        .addId("emotes-container");

    for (EmoteWidget emoteWidget : this.emoteWidgets.values()) {
      this.emotesList.addChild(emoteWidget);
    }

    container.addFlexibleContent(new ScrollWidget(this.emotesList));

    EmoteWidget selectedEmoteWidget = emotesList.listSession().getSelectedEntry();

    HorizontalListWidget menu = new HorizontalListWidget();
    menu.addId("buttons");

    menu.addEntry(ButtonWidget.i18n("labymod.ui.button.add", () -> this.setAction(Action.ADD)));

    this.removeButton = ButtonWidget.i18n("labymod.ui.button.remove",
        () -> this.setAction(Action.REMOVE));
    this.removeButton.setEnabled(false);
    menu.addEntry(this.removeButton);

    container.addContent(menu);

    this.document().addChild(container);

    if (this.action == null) return;

    Widget overlayWidget;
    switch (this.action){
      default:
      case ADD:
        DivWidget manageContainer = new DivWidget();
        manageContainer.addId("manage-container");

        overlayWidget = initializeAddContainer();

        manageContainer.addChild(overlayWidget);
        this.document().addChild(manageContainer);

        break;
      case REMOVE:
        this.emoteWidgets.remove(selectedEmoteWidget.name);
        addon.configuration().getEmotes().remove(selectedEmoteWidget.name);

        this.setAction(null);

        this.reload(); // setAction didn't reload the UI somehow. Temporary fix.

        break;
    }
  }

  private enum Action {
    ADD, REMOVE
  }

  private void setAction(Action action){
    this.action = action;
    this.reload();
  }

  private FlexibleContentWidget initializeAddContainer(){
    FlexibleContentWidget containerWidget = new FlexibleContentWidget().addId("add-container");

    HorizontalListWidget searchContainer = new HorizontalListWidget().addId("search-container");

    TextFieldWidget inputWidget = new TextFieldWidget().addId("text-input");
    inputWidget.placeholder(Component.translatable("betteremote.settings.openEmotes.add.searchInput"));
    searchContainer.addEntry(inputWidget);

    ButtonWidget buttonWidget = ButtonWidget.i18n("betteremote.settings.openEmotes.add.searchButton").addId("search-button");
    searchContainer.addEntry(buttonWidget);

    containerWidget.addContent(searchContainer);

    HorizontalListWidget resultsContainer = new HorizontalListWidget().addId("results-container");

    IconWidget emotePreview = new IconWidget(Icon.head(UUID.fromString("57e13c39-5755-4354-aefa-b60195ff6f27"))).addId("preview");
    resultsContainer.addEntry(emotePreview);

    DropdownWidget<BTTVEmote> resultsWidget = new DropdownWidget<>().addId("results");
    resultsContainer.addEntry(resultsWidget);

    resultsWidget.setChangeListener(emote -> emotePreview.icon().set(Icon.url(emote.getImageURL(1))));

    containerWidget.addContent(resultsContainer);

    buttonWidget.setActionListener(() -> {
      if (inputWidget.getText().length() < 3) return;

      BTTVSearch.search(inputWidget.getText(), (results) -> Task.builder(() -> {
        resultsWidget.clear();

        for (int i = 0; i < results.size(); i++) {
          BTTVEmote emote = results.get(i);

          if(i == 0) resultsWidget.setSelected(emote);

          resultsWidget.add(emote);
        }
      }).build().executeOnRenderThread());
    });

    TextFieldWidget nameInput = new TextFieldWidget().addId("name-input");
    nameInput.placeholder(Component.translatable("betteremote.settings.openEmotes.add.nameInput"));
    containerWidget.addContent(nameInput);

    HorizontalListWidget buttons = new HorizontalListWidget();
    buttons.addId("buttons");

    buttons.addEntry(ButtonWidget.i18n("labymod.ui.button.done", () -> {
      BTTVEmote selected = resultsWidget.getSelected();
      if (Objects.isNull(selected)) {
        this.setAction(null);
        return;
      }

      String name = nameInput.getText();
      if (name.contains(" ") || name.isEmpty()) return;

      // Registering the emote is a network request, so it must not block the render thread
      CompletableFuture.supplyAsync(() -> EmoteProvider.addBTTV(selected.id)).thenAccept(serverEmote -> Task.builder(() -> {
        if (Objects.isNull(serverEmote)) {
          this.addon.labyAPI().minecraft().chatExecutor().displayClientMessage(
              Component.translatable("betteremote.notifications.error.add").color(NamedTextColor.RED)
          );
          return;
        }
        EmoteProvider.CACHED_EMOTES.put(serverEmote.id, serverEmote);

        this.emoteWidgets.put(name, new EmoteWidget(name, serverEmote));
        this.addon.configuration().getEmotes().put(name, serverEmote);
        this.setAction(null);
      }).build().executeOnRenderThread());
    }));
    buttons.addEntry(ButtonWidget.i18n("labymod.ui.button.cancel", () -> this.setAction(null)));

    containerWidget.addContent(buttons);

    return containerWidget;
  }
}