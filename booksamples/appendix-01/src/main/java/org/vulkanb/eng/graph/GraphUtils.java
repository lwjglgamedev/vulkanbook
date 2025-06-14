package org.vulkanb.eng.graph;

import org.lwjgl.system.MemoryStack;
import org.vulkanb.eng.graph.vk.ImageSrc;

import java.io.IOException;
import java.nio.*;

import static org.lwjgl.stb.STBImage.*;

public class GraphUtils {

    private GraphUtils() {
        // Utility class
    }

    public static void cleanImageData(ImageSrc srcImage) {
        stbi_image_free(srcImage.data());
    }

    public static ImageSrc loadImage(String fileName) throws IOException {
        ImageSrc srcImage;
        ByteBuffer buf;
        try (var stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            buf = stbi_load(fileName, w, h, channels, 4);
            if (buf == null) {
                throw new IOException("Image file [" + fileName + "] not loaded: " + stbi_failure_reason());
            }

            srcImage = new ImageSrc(buf, w.get(0), h.get(0), channels.get(0));
        }

        return srcImage;
    }
}
