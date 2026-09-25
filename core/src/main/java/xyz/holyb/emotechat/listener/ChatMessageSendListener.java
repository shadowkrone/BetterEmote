package xyz.holyb.emotechat.listener;

import net.labymod.api.event.Priority;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.chat.ChatMessageSendEvent;
import xyz.holyb.emotechat.EmoteChatAddon;

public class ChatMessageSendListener {
  private final EmoteChatAddon addon;

  public ChatMessageSendListener(EmoteChatAddon addon){
      this.addon = addon;
  }

  // TODO: Block sending/rendering banned emote
  @Subscribe(Priority.EARLY)
  public void onChatMessageSend(ChatMessageSendEvent event){
    if (!addon.configuration().enabled().get()) return;

    // Simulate GlobalChat message (for testing purposes)
//    Component serverComponent = Component
//        .text("[", NamedTextColor.DARK_GRAY)
//        .append(Component.text("hypixel.net", NamedTextColor.GRAY))
//        .append(Component.text("]", NamedTextColor.DARK_GRAY))
//        .append(Component.space());
//
//    Component playerName = Component
//        .text("netheriteprefixFL holybaechu", NamedTextColor.GRAY) // EmoteChat Prefix
//        .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
//        .append(Component.text("Test", NamedTextColor.WHITE));
//
//    serverComponent.append(playerName);
//    Laby.labyAPI().minecraft().chatExecutor().displayClientMessage(serverComponent);

    String message = event.getMessage();
    String prefix = this.addon.configuration().prefix().get();

    String[] words = message.split(" ");
    for (int i = 0; i < words.length; i++) {
      String word = words[i].toLowerCase();
      if (!prefix.isEmpty() && !(word.startsWith(prefix) && word.endsWith(prefix))) continue;

      int finalI = i;
      addon.configuration().getEmotes().forEach((name, emote) -> {
        if(name.toLowerCase().equals(word.substring(prefix.length(), word.length() - prefix.length()))){
          words[finalI] = String.format("%s", emote.id); // ex: pepoG|a
        }
      });
    }

    event.changeMessage(String.join(" ", words), message);
  }
}
