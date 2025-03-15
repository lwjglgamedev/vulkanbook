package org.vulkanb.eng;

import org.vulkanb.eng.scene.Scene;
import org.vulkanb.eng.wnd.Window;

public record EngCtx(Window window, Scene scene) {

    public void cleanup() {
        window.cleanup();
    }
}

