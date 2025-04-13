package org.vulkanb.eng.wnd;

import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported;

public class Window {

    private final long handle;
    private final KeyboardInput keyboardInput;
    private final MouseInput mouseInput;
    private int height;
    private int width;

    public Window(String title) {
        if (!glfwInit()) {
            throw new RuntimeException("Unable to initialize GLFW");
        }

        if (!glfwVulkanSupported()) {
            throw new RuntimeException("Cannot find a compatible Vulkan installable client driver (ICD)");
        }

        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vidMode == null) {
            throw new RuntimeException("Error getting primary monitor");
        }
        width = vidMode.width();
        height = vidMode.height();

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_FALSE);

        // Create the window
        handle = glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (handle == MemoryUtil.NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        keyboardInput = new KeyboardInput(handle);

        glfwSetFramebufferSizeCallback(handle, (window, w, h) -> {
            width = w;
            height = h;
        });

        mouseInput = new MouseInput(handle);
    }

    public void cleanup() {
        glfwFreeCallbacks(handle);
        glfwDestroyWindow(handle);
        glfwTerminate();
    }

    public long getHandle() {
        return handle;
    }

    public int getHeight() {
        return height;
    }

    public KeyboardInput getKeyboardInput() {
        return keyboardInput;
    }

    public MouseInput getMouseInput() {
        return mouseInput;
    }

    public int getWidth() {
        return width;
    }

    public void pollEvents() {
        keyboardInput.input();
        mouseInput.input();
    }

    public void resetInput() {
        keyboardInput.resetInput();
    }

    public void setShouldClose() {
        glfwSetWindowShouldClose(handle, true);
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }
}
