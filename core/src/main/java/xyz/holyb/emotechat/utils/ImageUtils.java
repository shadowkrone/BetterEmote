package xyz.holyb.emotechat.utils;

import javax.imageio.ImageIO;
import javax.imageio.metadata.IIOMetadataNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

public class ImageUtils {
  public static String getBase64FromImage(BufferedImage image) throws IOException {
      final ByteArrayOutputStream os = new ByteArrayOutputStream();

      ImageIO.write(image, "png", os);
      return Base64.getEncoder().encodeToString(os.toByteArray());
  }

  public static IIOMetadataNode getMetadataNode(IIOMetadataNode root, String name) {
    int nNodes = root.getLength();
    for (int i = 0; i < nNodes; i++) {
      if (root.item(i).getNodeName().compareToIgnoreCase(name) == 0) {
        return ((IIOMetadataNode) root.item(i));
      }
    }

    IIOMetadataNode node = new IIOMetadataNode(name);
    root.appendChild(node);

    return (node);
  }
}