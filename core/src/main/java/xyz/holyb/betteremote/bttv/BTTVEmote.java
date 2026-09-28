package xyz.holyb.betteremote.bttv;

import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.renderer.DefaultEntryRenderer;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.renderer.EntryRenderer;
import xyz.holyb.betteremote.emote.Emote;

public class BTTVEmote {
  public Emote emote;

  public String id;
  public String code;
  public BTTVUser user;

  public String getImageURL(Integer size) {
      return String.format("https://cdn.betterttv.net/emote/%s/%dx", this.id, size);
  }

  public Component displayName() {
    return this.user == null
        ? Component.text(this.code)
        : Component.translatable("emote.emote.byUser", Component.text(this.code), Component.text(this.user.name));
  }

  // Dropdowns would show toString() otherwise, which can't be translated
  public static EntryRenderer<BTTVEmote> entryRenderer() {
    DefaultEntryRenderer<BTTVEmote> renderer = new DefaultEntryRenderer<>();
    renderer.setDisplayNameProvider(BTTVEmote::displayName);
    return renderer;
  }
}
