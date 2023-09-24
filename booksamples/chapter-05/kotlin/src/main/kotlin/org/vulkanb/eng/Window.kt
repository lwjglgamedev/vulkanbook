package org.vulkanb.eng

import org.lwjgl.glfw.Callbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWKeyCallbackI
import org.lwjgl.glfw.GLFWVulkan.*
import org.lwjgl.system.MemoryUtil

class Window @JvmOverloads constructor(title: String, keyCallback: GLFWKeyCallbackI? = null) {
    val mouseInput: MouseInput
    val windowHandle: Long
    var height: Int
        private set
    var isResized = false
    var width: Int
        private set

    init {
        check(glfwInit()) { "Unable to initialize GLFW" }
        check(glfwVulkanSupported()) { "Cannot find a compatible Vulkan installable client driver (ICD)" }
        val vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor())!!
        width = vidMode.width()
        height = vidMode.height()
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_FALSE)

        // Create the window
        windowHandle = glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL)
        if (windowHandle == MemoryUtil.NULL) {
            throw RuntimeException("Failed to create the GLFW window")
        }
        glfwSetFramebufferSizeCallback(windowHandle) { window: Long, w: Int, h: Int -> resize(w, h) }
        glfwSetKeyCallback(windowHandle) { window: Long, key: Int, scancode: Int, action: Int, mods: Int ->
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true)
            }
            keyCallback?.invoke(window, key, scancode, action, mods)
        }
        mouseInput = MouseInput(windowHandle)
    }

    fun cleanup() {
        Callbacks.glfwFreeCallbacks(windowHandle)
        glfwDestroyWindow(windowHandle)
        glfwTerminate()
    }

    fun isKeyPressed(keyCode: Int): Boolean {
        return glfwGetKey(windowHandle, keyCode) == GLFW_PRESS
    }

    fun pollEvents() {
        glfwPollEvents()
        mouseInput.input()
    }

    fun resetResized() {
        isResized = false
    }

    fun resize(width: Int, height: Int) {
        isResized = true
        this.width = width
        this.height = height
    }

    fun setShouldClose() {
        glfwSetWindowShouldClose(windowHandle, true)
    }

    fun shouldClose(): Boolean {
        return glfwWindowShouldClose(windowHandle)
    }
}
