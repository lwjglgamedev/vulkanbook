package org.vulkanb.eng.graph.vk

import org.lwjgl.vulkan.VK11
import org.lwjgl.vulkan.VK11.*

object VulkanUtils {
    val oS: OSType
        get() {
            val result: OSType
            val os = System.getProperty("os.name", "generic").lowercase()
            result = if (os.indexOf("mac") >= 0 || os.indexOf("darwin") >= 0) {
                OSType.MACOS
            } else if (os.indexOf("win") >= 0) {
                OSType.WINDOWS
            } else if (os.indexOf("nux") >= 0) {
                OSType.LINUX
            } else {
                OSType.OTHER
            }
            return result
        }

    fun Int.vkCheck(errMsg: String) {
        if (this != VK_SUCCESS) {
            throw RuntimeException("$errMsg: $this")
        }
    }

    enum class OSType {
        WINDOWS,
        MACOS,
        LINUX,
        OTHER
    }
}
