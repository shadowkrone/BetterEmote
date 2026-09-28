package xyz.holyb.betteremote.tag;

import net.labymod.api.util.io.web.request.Request;
import xyz.holyb.betteremote.BetterEmoteAddon;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owners and staff of BetterEmote. The list is hosted on shadowkrone.com,
 * so it can change without a new release. Only the list is downloaded, nothing is sent.
 */
public class RoleRegistry {
  private static final String ROLES_URL = "https://shadowkrone.com/laby/addon/BetterEmote/roles.json";

  private static final Map<UUID, Role> ROLES = new ConcurrentHashMap<>();

  public static Role get(UUID uuid) {
    return ROLES.get(uuid);
  }

  public static void load() {
    Request.ofGson(RoleList.class)
        .url(ROLES_URL)
        .async()
        .execute(response -> {
          if (response.hasException() || !response.isPresent() || Objects.isNull(response.get())) {
            BetterEmoteAddon.get().logger().warn("Could not load BetterEmote roles from " + ROLES_URL);
            return;
          }

          RoleList list = response.get();
          ROLES.clear();
          put(list.staff, Role.STAFF);
          put(list.owners, Role.OWNER); // last, so an owner listed as staff still shows as owner
        });
  }

  private static void put(List<String> uuids, Role role) {
    if (Objects.isNull(uuids)) return;

    for (String uuid : uuids) {
      try {
        ROLES.put(UUID.fromString(uuid.trim()), role);
      } catch (IllegalArgumentException e) {
        BetterEmoteAddon.get().logger().warn("Invalid UUID in BetterEmote roles: " + uuid);
      }
    }
  }

  private static class RoleList {
    List<String> owners;
    List<String> staff;
  }
}
