package xyz.holyb.betteremote.site;

import com.google.gson.annotations.SerializedName;
import net.labymod.api.client.component.Component;
import xyz.holyb.betteremote.emote.Emote;

/** An emote uploaded on the website, as listed by /api/emotes. */
public class SiteEmote extends Emote {
  // Kept apart from name(), which reads the name from the chat id
  @SerializedName("name")
  public String code;

  public boolean official;
  public String owner;

  /**
   * A plain copy for the saved emotes. The config is written with the runtime type, so saving
   * this object directly would also store the website fields.
   */
  public Emote toEmote() {
    Emote emote = new Emote();
    emote.id = this.id;
    emote.provider = this.provider;
    emote.imageType = this.imageType;
    emote.animated = this.animated;
    emote.banned = this.banned;
    return emote;
  }

  @Override
  public Component description() {
    if (this.official) return Component.translatable("emote.browse.official");
    return this.owner == null
        ? Component.translatable("emote.browse.community")
        : Component.translatable("emote.browse.byOwner", Component.text(this.owner));
  }
}
