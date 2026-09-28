package xyz.holyb.betteremote;

import net.labymod.api.addon.AddonConfig;
import net.labymod.api.client.gui.screen.activity.Activity;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.widget.widgets.activity.settings.ActivitySettingWidget.ActivitySetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.KeybindWidget.KeyBindSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.TextFieldWidget.TextFieldSetting;
import net.labymod.api.configuration.loader.annotation.ConfigName;
import net.labymod.api.configuration.loader.annotation.Exclude;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.configuration.settings.annotation.SettingExperimental;
import net.labymod.api.configuration.settings.annotation.SettingSection;
import net.labymod.api.util.MethodOrder;
import xyz.holyb.betteremote.activity.EmoteMenuActivity;
import xyz.holyb.betteremote.emote.Emote;
import java.util.HashMap;
import java.util.Map;

@ConfigName("settings")
public class BetterEmoteConfiguration extends AddonConfig {

  @SwitchSetting
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

  @Exclude
  public Map<String, Emote> emotes = new HashMap<>();

  @KeyBindSetting
  private final ConfigProperty<Key> menuKey = new ConfigProperty<>(Key.K);

  @TextFieldSetting
  private final ConfigProperty<String> prefix = new ConfigProperty<>(":");

  @SwitchSetting
  private final ConfigProperty<Boolean> showRoleTags = new ConfigProperty<>(true);

  @SliderSetting(min=8, max=32)
  private final ConfigProperty<Integer> emoteSize = new ConfigProperty<>(8);

  @SliderSetting(min=1, max=3)
  private final ConfigProperty<Integer> emoteQuality = new ConfigProperty<>(1);

  @SettingSection("experimental")
  @SwitchSetting
  @SettingExperimental
  private final ConfigProperty<Boolean> animatedEmotes = new ConfigProperty<>(true);

  @SettingSection("others")
  @SwitchSetting
  private final ConfigProperty<Boolean> incompatWarn = new ConfigProperty<>(true);

  @Override
  public ConfigProperty<Boolean> enabled() {
    return this.enabled;
  }

  public Map<String, Emote> getEmotes() { return this.emotes; }

  public ConfigProperty<Key> menuKey() { return this.menuKey; }

  public ConfigProperty<String> prefix() { return this.prefix; }

  @MethodOrder(after = "enabled")
  @ActivitySetting
  public Activity openEmoteMenu(){
    return new EmoteMenuActivity(true);
  }

  public ConfigProperty<Boolean> showRoleTags() { return this.showRoleTags; }

  public ConfigProperty<Integer> emoteSize() { return this.emoteSize; }

  public ConfigProperty<Integer> emoteQuality() { return this.emoteQuality; }

  public ConfigProperty<Boolean> animatedEmotes() { return this.animatedEmotes; }

  public ConfigProperty<Boolean> incompatWarn() { return this.incompatWarn; }
}
