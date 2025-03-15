package org.vulkanb.eng;

import org.vulkanb.eng.scene.Scene;
import org.vulkanb.eng.sound.SoundManager;
import org.vulkanb.eng.wnd.Window;

public record EngCtx(Window window, Scene scene, SoundManager soundManager) {

    public void cleanup() {
        window.cleanup();
        soundManager.cleanup();
    }
}

