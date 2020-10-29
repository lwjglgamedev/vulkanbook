package org.vulkanb.eng;

import org.lwjgl.glfw.*;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported;

public class Window implements GLFWFramebufferSizeCallbackI {

    private int height;
    private MouseInput mouseInput;
    private boolean resized;
    private int width;
    private long windowHandle;

    public Window(String title) {
        this(title, null);
    }

    public Window(String title, GLFWKeyCallbackI keyCallback) {
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        if (!glfwVulkanSupported()) {
            throw new IllegalStateException("Cannot find a compatible Vulkan installable client driver (ICD)");
        }

        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        width = vidMode.width();
        height = vidMode.height();

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_FALSE);

        // Create the window
        windowHandle = glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (windowHandle == MemoryUtil.NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        glfwSetFramebufferSizeCallback(windowHandle, (window, width, height) -> resize(width, height));

        glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true);
            }
            if (keyCallback != null) {
                keyCallback.invoke(window, key, scancode, action, mods);
            }
        });

        mouseInput = new MouseInput(windowHandle);
    }

    public void cleanup() {
        glfwDestroyWindow(windowHandle);
        glfwTerminate();
    }

    public int getHeight() {
        return height;
    }

    public MouseInput getMouseInput() {
        return mouseInput;
    }

    public int getWidth() {
        return width;
    }

    public long getWindowHandle() {
        return windowHandle;
    }

    @Override
    public void invoke(long handle, int width, int height) {
        resize(width, height);
    }

    public boolean isKeyPressed(int keyCode) {
        return glfwGetKey(windowHandle, keyCode) == GLFW_PRESS;
    }

    public boolean isResized() {
        return resized;
    }

    public void pollEvents() {
        glfwPollEvents();
        mouseInput.input();
    }

    public void resetResized() {
        resized = false;
    }

    public void resize(int width, int height) {
        resized = true;
        this.width = width;
        this.height = height;
    }

    public void setResized(boolean resized) {
        this.resized = resized;
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(windowHandle);
    }
}
