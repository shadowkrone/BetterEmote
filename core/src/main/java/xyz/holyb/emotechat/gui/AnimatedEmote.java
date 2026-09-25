package xyz.holyb.emotechat.gui;

import net.labymod.api.client.gui.icon.Icon;
import xyz.holyb.emotechat.EmoteChatAddon;
import xyz.holyb.emotechat.utils.ImageUtils;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An emote icon that switches frames by itself while it is drawn. Chat messages can't be
 * edited reliably on newer LabyMod versions, so the animation lives in the icon instead.
 */
public class AnimatedEmote {
  private static final Map<String, Icon> ICONS = new ConcurrentHashMap<>();
  private static final ExecutorService LOADER = Executors.newFixedThreadPool(2, runnable -> {
    Thread thread = new Thread(runnable, "BetterEmote GIF loader");
    thread.setDaemon(true);
    return thread;
  });

  // Browsers treat very short GIF delays as 100 ms, so animations run at the expected speed
  private static final int MIN_DELAY_MS = 20;
  private static final int DEFAULT_DELAY_MS = 100;

  private volatile Icon[] frames;
  private int[] frameEnds; // cumulative end time of each frame in ms
  private int duration;

  /**
   * Returns an icon that shows the static image until the GIF is loaded and then animates.
   */
  public static Icon icon(String gifUrl, String staticUrl) {
    return ICONS.computeIfAbsent(gifUrl, url -> {
      Icon fallback = Icon.url(staticUrl);
      AnimatedEmote animation = new AnimatedEmote();
      LOADER.execute(() -> animation.load(url));

      return Icon.texture(() -> {
        Icon frame = animation.currentFrame();
        return (Objects.nonNull(frame) ? frame : fallback).getResourceLocation();
      });
    });
  }

  private Icon currentFrame() {
    Icon[] frames = this.frames;
    if (Objects.isNull(frames)) return null;

    long time = System.currentTimeMillis() % this.duration;
    for (int i = 0; i < frames.length; i++) {
      if (time < this.frameEnds[i]) return frames[i];
    }
    return frames[frames.length - 1];
  }

  private void load(String url) {
    try (InputStream stream = new URI(url).toURL().openStream();
         ImageInputStream input = ImageIO.createImageInputStream(stream)) {
      ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
      reader.setInput(input, false);

      int count = reader.getNumImages(true);
      if (count <= 1) return; // not animated, keep the static image

      List<Icon> icons = new ArrayList<>(count);
      int[] ends = new int[count];
      int total = 0;
      BufferedImage canvas = null;

      for (int i = 0; i < count; i++) {
        BufferedImage frame = reader.read(i);
        IIOMetadata meta = reader.getImageMetadata(i);
        IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(meta.getNativeMetadataFormatName());
        IIOMetadataNode descriptor = ImageUtils.getMetadataNode(root, "ImageDescriptor");
        IIOMetadataNode control = ImageUtils.getMetadataNode(root, "GraphicControlExtension");

        if (Objects.isNull(canvas)) {
          int width = Math.max(frame.getWidth(), reader.getWidth(0));
          int height = Math.max(frame.getHeight(), reader.getHeight(0));
          canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        // GIF frames often only contain the changed part, so they are drawn onto a shared canvas
        int x = parseInt(descriptor.getAttribute("imageLeftPosition"));
        int y = parseInt(descriptor.getAttribute("imageTopPosition"));
        String disposal = control.getAttribute("disposalMethod");

        BufferedImage previous = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;

        Graphics2D graphics = canvas.createGraphics();
        graphics.drawImage(frame, x, y, null);
        graphics.dispose();

        icons.add(Icon.url("data:image/png;base64," + ImageUtils.getBase64FromImage(canvas)));

        int delay = parseInt(control.getAttribute("delayTime")) * 10;
        total += delay < MIN_DELAY_MS ? DEFAULT_DELAY_MS : delay;
        ends[i] = total;

        if ("restoreToBackgroundColor".equals(disposal)) {
          Graphics2D clear = canvas.createGraphics();
          clear.setComposite(AlphaComposite.Clear);
          clear.fillRect(x, y, frame.getWidth(), frame.getHeight());
          clear.dispose();
        } else if (Objects.nonNull(previous)) {
          canvas = previous;
        }
      }

      reader.dispose();

      this.frameEnds = ends;
      this.duration = Math.max(total, 1);
      this.frames = icons.toArray(Icon[]::new);
    } catch (Exception e) {
      EmoteChatAddon.get().logger().warn("Failed to load animated emote " + url, e);
    }
  }

  private static int parseInt(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static BufferedImage copy(BufferedImage image) {
    BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = copy.createGraphics();
    graphics.drawImage(image, 0, 0, null);
    graphics.dispose();
    return copy;
  }
}
