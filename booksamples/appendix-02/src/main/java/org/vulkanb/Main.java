package org.vulkanb;

import org.tinylog.Logger;
import org.vulkanb.boxes.GameController;
import org.vulkanb.eng.Engine;

public class Main {
    public static void main(String[] args) {
        Logger.info("Starting application");
        var engine = new Engine("Boxes", new GameController());
        Logger.info("Started application");
        engine.run();
    }
}
