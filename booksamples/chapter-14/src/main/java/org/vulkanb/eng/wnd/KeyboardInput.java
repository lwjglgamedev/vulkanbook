package org.vulkanb.eng.wnd;

import org.lwjgl.glfw.*;

import java.util.*;

import static org.lwjgl.glfw.GLFW.*;

public class KeyboardInput implements GLFWKeyCallbackI {

    private final Map<Integer, Boolean> singlePressKeyMap;
    private final long windowHandle;
    private List<GLFWKeyCallbackI> callbacks;

    public KeyboardInput(long windowHandle) {
        this.windowHandle = windowHandle;
        singlePressKeyMap = new HashMap<>();
        glfwSetKeyCallback(windowHandle, this);
        callbacks = new ArrayList<>();
    }

    public void addKeyCallBack(GLFWKeyCallbackI callback) {
        callbacks.add(callback);
    }

    public void input() {
        glfwPollEvents();
    }

    @Override
    public void invoke(long handle, int keyCode, int scanCode, int action, int mods) {
        singlePressKeyMap.put(keyCode, action == GLFW_PRESS);
        int numCallBacks = callbacks.size();
        for (int i = 0; i < numCallBacks; i++) {
            callbacks.get(i).invoke(handle, keyCode, scanCode, action, mods);
        }
    }

    public boolean keyPressed(int keyCode) {
        return glfwGetKey(windowHandle, keyCode) == GLFW_PRESS;
    }

    public boolean keySinglePress(int keyCode) {
        Boolean value = singlePressKeyMap.get(keyCode);
        return value != null && value;
    }

    public void resetInput() {
        singlePressKeyMap.clear();
    }

    public void setCharCallBack(GLFWCharCallbackI charCallback) {
        glfwSetCharCallback(windowHandle, charCallback);
    }
}
