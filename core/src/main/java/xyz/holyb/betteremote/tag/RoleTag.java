package xyz.holyb.betteremote.tag;

import net.labymod.api.client.component.Component;
import net.labymod.api.client.entity.player.tag.tags.ComponentNameTag;
import net.labymod.api.client.network.NetworkPlayerInfo;
import net.labymod.api.client.render.state.EntityExtraKeys;
import net.labymod.api.client.render.state.entity.CustomAvatarDataSnapshot;
import net.labymod.api.client.render.state.entity.EntitySnapshot;
import xyz.holyb.betteremote.BetterEmoteAddon;
import java.util.List;
import java.util.Objects;

/**
 * Shows "Owner of BetterEmote" or "Staff of BetterEmote" above the name of listed players.
 */
public class RoleTag extends ComponentNameTag {
  private final BetterEmoteAddon addon;

  public RoleTag(BetterEmoteAddon addon) {
    this.addon = addon;
  }

  @Override
  protected List<Component> buildComponents(EntitySnapshot snapshot) {
    if (!this.addon.configuration().enabled().get() || !this.addon.configuration().showRoleTags().get()) return List.of();
    if (!snapshot.has(EntityExtraKeys.CUSTOM_AVATAR_DATA)) return List.of();

    CustomAvatarDataSnapshot avatar = snapshot.get(EntityExtraKeys.CUSTOM_AVATAR_DATA);
    NetworkPlayerInfo info = avatar.playerInfo();
    if (Objects.isNull(info) || Objects.isNull(info.profile())) return List.of();

    Role role = RoleRegistry.get(info.profile().getUniqueId());
    return Objects.isNull(role) ? List.of() : List.of(role.component());
  }
}
