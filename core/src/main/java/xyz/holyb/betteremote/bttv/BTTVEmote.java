package xyz.holyb.betteremote.bttv;

import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.icon.Icon;
import xyz.holyb.betteremote.BetterEmoteAddon;
import xyz.holyb.betteremote.emote.Emote;
import xyz.holyb.betteremote.gui.AnimatedEmote;

public class BTTVEmote {
  public Emote emote;

  public String id;
  public String code;
  public BTTVUser user;
  // Sent by BTTV's search and trending list, animated emotes are rendered as GIFs
  public boolean animated;

  public String getImageURL(Integer size) {
      return String.format("https://cdn.betterttv.net/emote/%s/%dx", this.id, size);
  }

  /** Animated like saved emotes when the emote is and animated emotes are turned on. */
  public Icon icon(int size) {
    String still = String.format("https://cdn.betterttv.net/emote/%s/%dx.png", this.id, size);
    return this.animated && BetterEmoteAddon.get().configuration().animatedEmotes().get()
        ? AnimatedEmote.icon(this.getImageURL(size), still)
        : Icon.url(still);
  }

  public Component displayName() {
    return this.user == null
        ? Component.text(this.code)
        : Component.translatable("emote.emote.byUser", Component.text(this.code), Component.text(this.user.name));
  }
}
