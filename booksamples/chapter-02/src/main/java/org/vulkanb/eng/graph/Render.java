package org.vulkanb.eng.graph;

import org.vulkanb.eng.EngCtx;
import org.vulkanb.eng.graph.vk.VkCtx;
import org.vulkanb.eng.wnd.Window;
import org.vulkanb.eng.scene.Scene;

public class Render {

    private final VkCtx vkCtx;

    public Render(EngCtx engCtx) {
        vkCtx = new VkCtx();
    }

    public void cleanup() {
        vkCtx.cleanup();
    }

    public void render(EngCtx engCtx) {
        // To be implemented
    }
}
