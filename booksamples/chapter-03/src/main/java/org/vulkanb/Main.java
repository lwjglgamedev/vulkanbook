package org.vulkanb;

import org.tinylog.Logger;
import org.vulkanb.eng.*;

public class Main implements IGameLogic {

    public static void main(String[] args) {
        Logger.info("Starting application");
        var engine = new Engine("Vulkan Book", new Main());
        Logger.info("Started application");
        engine.run();
    }

    @Override
    public void cleanup() {
        // To be implemented
    }

    @Override
    public void init(EngCtx engCtx) {
        // To be implemented
    }

    @Override
    public void input(EngCtx engCtx, long diffTimeMillis) {
        // To be implemented
    }

    @Override
    public void update(EngCtx engCtx, long diffTimeMillis) {
        // To be implemented
    }
}
