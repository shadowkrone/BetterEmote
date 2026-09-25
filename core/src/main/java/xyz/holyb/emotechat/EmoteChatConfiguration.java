package xyz.holyb.emotechat;

import net.labymod.api.addon.AddonConfig;
import net.labymod.api.client.gui.screen.activity.Activity;
import net.labymod.api.client.gui.screen.widget.widgets.activity.settings.ActivitySettingWidget.ActivitySetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.TextFieldWidget.TextFieldSetting;
import net.labymod.api.configuration.loader.annotation.ConfigName;
import net.labymod.api.configuration.loader.annotation.Exclude;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.configuration.settings.annotation.SettingExperimental;
import net.labymod.api.configuration.settings.annotation.SettingSection;
import net.labymod.api.util.MethodOrder;
import xyz.holyb.emotechat.activity.EmotesActivity;
import xyz.holyb.emotechat.emote.Emote;
import java.util.HashMap;
import java.util.Map;

@ConfigName("settings")
public class EmoteChatConfiguration extends AddonConfig {

  @SwitchSetting
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

  @Exclude
  public Map<String, Emote> emotes = new HashMap<>();

  @TextFieldSetting
  private final ConfigProperty<String> prefix = new ConfigProperty<>(":");

  @SliderSetting(min=8, max=32)
  private final ConfigProperty<Integer> emoteSize = new ConfigProperty<>(8);

  @SliderSetting(min=1, max=3)
  private final ConfigProperty<Integer> emoteQuality = new ConfigProperty<>(1);

  @SliderSetting(steps = 0.1f, min = 0, max = 5)
  private final ConfigProperty<Float> renderDelay = new ConfigProperty<>(0.1f);

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

  public ConfigProperty<String> prefix() { return this.prefix; }

  @MethodOrder(after = "enabled")
  @ActivitySetting
  public Activity openEmotes(){
    return new EmotesActivity();
  }

  public ConfigProperty<Integer> emoteSize() { return this.emoteSize; }

  public ConfigProperty<Integer> emoteQuality() { return this.emoteQuality; }

  public ConfigProperty<Boolean> animatedEmotes() { return this.animatedEmotes; }

  public ConfigProperty<Boolean> incompatWarn() { return this.incompatWarn; }

  public ConfigProperty<Float> renderDelay() { return this.renderDelay; }
}
