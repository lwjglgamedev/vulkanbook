package org.vulkanb.eng.graph.vk;

import java.nio.ByteBuffer;

public record ImageSrc(ByteBuffer data, int width, int height, int channels) {
}