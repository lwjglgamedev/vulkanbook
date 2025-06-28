package org.vulkanb.eng.model;

import java.util.List;

public record Animation(String name, float frameMillis, List<AnimatedFrame> frames) {
}
