package xyz.holyb.betteremote.tag;

import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;

public enum Role {
  OWNER(Component.translatable("betteremote.tag.owner", NamedTextColor.GOLD)),
  STAFF(Component.translatable("betteremote.tag.staff", NamedTextColor.AQUA));

  // Built once, because name tags are drawn every frame
  private final Component component;

  Role(Component component) {
    this.component = component;
  }

  public Component component() {
    return this.component;
  }
}
