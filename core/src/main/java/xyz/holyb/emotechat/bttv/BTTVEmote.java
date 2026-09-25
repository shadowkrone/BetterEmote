package xyz.holyb.emotechat.bttv;

import net.labymod.api.util.io.web.request.Request;
import xyz.holyb.emotechat.emote.Emote;

public class BTTVEmote {
  public Emote emote;

  public String id;
  public String code;
  public BTTVUser user;

  public String getImageURL(Integer size) {
      return String.format("https://cdn.betterttv.net/emote/%s/%dx", this.id, size);
  }

  @Override
  public String toString() {
    return this.user == null ? this.code : String.format("%s by %s", this.code, this.user.name);
  }
}
