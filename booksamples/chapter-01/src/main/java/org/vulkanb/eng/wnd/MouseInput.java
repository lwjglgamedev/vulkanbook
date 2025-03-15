package org.vulkanb.eng.wnd;

import org.joml.Vector2f;

import static org.lwjgl.glfw.GLFW.*;

public class MouseInput {

    private final Vector2f currentPos;
    private final Vector2f deltaPos;
    private final Vector2f previousPos;
    private boolean inWindow;
    private boolean leftButtonPressed;
    private boolean rightButtonPressed;

    public MouseInput(long windowHandle) {
        previousPos = new Vector2f(-1, -1);
        currentPos = new Vector2f();
        deltaPos = new Vector2f();
        leftButtonPressed = false;
        rightButtonPressed = false;
        inWindow = false;

        glfwSetCursorPosCallback(windowHandle, (handle, xpos, ypos) -> {
            currentPos.x = (float) xpos;
            currentPos.y = (float) ypos;
        });
        glfwSetCursorEnterCallback(windowHandle, (handle, entered) -> inWindow = entered);
        glfwSetMouseButtonCallback(windowHandle, (handle, button, action, mode) -> {
            leftButtonPressed = button == GLFW_MOUSE_BUTTON_1 && action == GLFW_PRESS;
            rightButtonPressed = button == GLFW_MOUSE_BUTTON_2 && action == GLFW_PRESS;
        });
    }

    public Vector2f getCurrentPos() {
        return currentPos;
    }

    public Vector2f getDeltaPos() {
        return deltaPos;
    }

    public void input() {
        deltaPos.x = 0;
        deltaPos.y = 0;
        if (previousPos.x >= 0 && previousPos.y >= 0 && inWindow) {
            deltaPos.x = currentPos.x - previousPos.x;
            deltaPos.y = currentPos.y - previousPos.y;
        }
        previousPos.x = currentPos.x;
        previousPos.y = currentPos.y;
    }

    public boolean isLeftButtonPressed() {
        return leftButtonPressed;
    }

    public boolean isRightButtonPressed() {
        return rightButtonPressed;
    }
}
