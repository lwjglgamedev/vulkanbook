package org.vulkanb.boxes.runlevel;

import org.joml.Vector2f;
import org.vulkanb.eng.EngCtx;
import org.vulkanb.eng.scene.Camera;
import org.vulkanb.eng.wnd.*;

import static org.lwjgl.glfw.GLFW.*;

public class CameraController {

    private static final float MOUSE_SENSITIVITY = 0.1f;
    private static final float MOVEMENT_SPEED = 0.01f;

    public void input(EngCtx engCtx, long diffTimeMillis) {
        float move = diffTimeMillis * MOVEMENT_SPEED;
        Camera camera = engCtx.scene().getCamera();
        KeyboardInput ki = engCtx.window().getKeyboardInput();
        if (ki.keyPressed(GLFW_KEY_W)) {
            camera.moveForward(move);
        } else if (ki.keyPressed(GLFW_KEY_S)) {
            camera.moveBackwards(move);
        }
        if (ki.keyPressed(GLFW_KEY_A)) {
            camera.moveLeft(move);
        } else if (ki.keyPressed(GLFW_KEY_D)) {
            camera.moveRight(move);
        }

        MouseInput mouseInput = engCtx.window().getMouseInput();
        if (mouseInput.isRightButtonPressed()) {
            Vector2f displVec = mouseInput.getDeltaPos();
            camera.addRotation((float) Math.toRadians(-displVec.x * MOUSE_SENSITIVITY),
                    (float) Math.toRadians(-displVec.y * MOUSE_SENSITIVITY));
        }
    }
}
