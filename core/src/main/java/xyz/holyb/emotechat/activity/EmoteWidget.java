package xyz.holyb.emotechat.activity;

import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.lss.property.annotation.AutoWidget;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.widget.SimpleWidget;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import xyz.holyb.emotechat.emote.Emote;

@AutoWidget
public class EmoteWidget extends SimpleWidget {
  private final Emote emote;
  public final String name;

  public EmoteWidget(String name, Emote emote){
    this.name = name;
    this.emote = emote;
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);

    IconWidget iconWidget = new IconWidget(Icon.url(emote.getImageURL(1)));
    iconWidget.addId("emote-icon");
    this.addChild(iconWidget);

    ComponentWidget nameWidget = ComponentWidget.text(this.name);
    nameWidget.addId("name");
    this.addChild(nameWidget);

    ComponentWidget authorWidget = ComponentWidget.text(emote.toString());
    authorWidget.addId("description");
    this.addChild(authorWidget);
  }
}
