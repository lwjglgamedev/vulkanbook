package org.vulkanb.eng;

import org.lwjgl.glfw.*;
import org.lwjgl.system.MemoryUtil;

public class Window implements GLFWFramebufferSizeCallbackI {

    private int height;
    private GLFWKeyCallback keyCallback;
    private MouseInput mouseInput;
    private boolean resized;
    private int width;
    private long windowHandle;

    public Window(String title) {
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        if (!GLFWVulkan.glfwVulkanSupported()) {
            throw new IllegalStateException("Cannot find a compatible Vulkan installable client driver (ICD)");
        }

        GLFWVidMode vidMode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
        this.width = vidMode.width();
        this.height = vidMode.height();

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
        GLFW.glfwWindowHint(GLFW.GLFW_MAXIMIZED, GLFW.GLFW_FALSE);

        // Create the window
        this.windowHandle = GLFW.glfwCreateWindow(this.width, this.height, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (this.windowHandle == MemoryUtil.NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        GLFW.glfwSetFramebufferSizeCallback(this.windowHandle, this);

        this.keyCallback = new GLFWKeyCallback() {
            @Override
            public void invoke(long window, int key, int scancode, int action, int mods) {
                if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_RELEASE) {
                    GLFW.glfwSetWindowShouldClose(window, true);
                }
            }
        };
        GLFW.glfwSetKeyCallback(this.windowHandle, this.keyCallback);

        this.mouseInput = new MouseInput(this.windowHandle);
    }

    public void cleanUp() {
        GLFW.glfwDestroyWindow(this.windowHandle);
        this.keyCallback.free();
        GLFW.glfwTerminate();
    }

    public int getHeight() {
        return this.height;
    }

    public MouseInput getMouseInput() {
        return this.mouseInput;
    }

    public int getWidth() {
        return this.width;
    }

    public long getWindowHandle() {
        return this.windowHandle;
    }

    @Override
    public void invoke(long handle, int width, int height) {
        resize(width, height);
    }

    public boolean isResized() {
        return this.resized;
    }

    public void pollEvents() {
        GLFW.glfwPollEvents();
        this.mouseInput.input();
    }

    public void resetResized() {
        this.resized = false;
    }

    public void resize(int width, int height) {
        this.resized = true;
        this.width = width;
        this.height = height;
        GLFW.glfwSetWindowSize(this.windowHandle, this.width, this.height);
    }

    public boolean shouldClose() {
        return GLFW.glfwWindowShouldClose(this.windowHandle);
    }
}
