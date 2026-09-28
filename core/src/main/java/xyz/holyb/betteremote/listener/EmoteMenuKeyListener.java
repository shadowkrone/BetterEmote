package xyz.holyb.betteremote.listener;

import net.labymod.api.client.Minecraft;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.input.KeyEvent;
import xyz.holyb.betteremote.BetterEmoteAddon;
import xyz.holyb.betteremote.activity.EmoteMenuActivity;

public class EmoteMenuKeyListener {
  private final BetterEmoteAddon addon;

  public EmoteMenuKeyListener(BetterEmoteAddon addon) {
    this.addon = addon;
  }

  @Subscribe
  public void onKey(KeyEvent event) {
    if (event.state() != KeyEvent.State.PRESS) return;
    if (!addon.configuration().enabled().get()) return;
    if (event.key() != addon.configuration().menuKey().get()) return;

    // Only open from the game itself, so the key can still be typed in chat and other screens
    Minecraft minecraft = addon.labyAPI().minecraft();
    if (!minecraft.isIngame() || minecraft.minecraftWindow().isScreenOpened()) return;

    minecraft.minecraftWindow().displayScreen(new EmoteMenuActivity());
  }
}
