package org.vulkanb.eng

import org.joml.Vector2f
import org.lwjgl.glfw.GLFW.*

class MouseInput(windowHandle: Long) {
    private val currentPos: Vector2f
    private val displVec: Vector2f
    private val previousPos: Vector2f = Vector2f(-1f, -1f)
    private var inWindow: Boolean
    var isLeftButtonPressed: Boolean
    var isRightButtonPressed: Boolean

    init {
        currentPos = Vector2f()
        displVec = Vector2f()
        isLeftButtonPressed = false
        isRightButtonPressed = false
        inWindow = false
        glfwSetCursorPosCallback(windowHandle) { handle, xpos, ypos ->
            currentPos.x = xpos.toFloat()
            currentPos.y = ypos.toFloat()
        }
        glfwSetCursorEnterCallback(windowHandle) { handle, entered -> inWindow = entered }
        glfwSetMouseButtonCallback(windowHandle) { handle, button, action, mode ->
            isLeftButtonPressed = button == GLFW_MOUSE_BUTTON_1 && action == GLFW_PRESS
            isRightButtonPressed = button == GLFW_MOUSE_BUTTON_2 && action == GLFW_PRESS
        }
    }

    fun getCurrentPos(): Vector2f {
        return currentPos
    }

    fun getDisplVec(): Vector2f {
        return displVec
    }

    fun input() {
        displVec.x = 0F
        displVec.y = 0F
        if (previousPos.x > 0 && previousPos.y > 0 && inWindow) {
            val deltax: Double = (currentPos.x - previousPos.x).toDouble()
            val deltay: Double = (currentPos.y - previousPos.y).toDouble()
            val rotateX = deltax != 0.0
            val rotateY = deltay != 0.0
            if (rotateX) {
                displVec.y = deltax.toFloat()
            }
            if (rotateY) {
                displVec.x = deltay.toFloat()
            }
        }
        previousPos.x = currentPos.x
        previousPos.y = currentPos.y
    }
}
