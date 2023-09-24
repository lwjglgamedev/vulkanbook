package org.vulkanb

import io.github.oshai.kotlinlogging.KotlinLogging
import org.vulkanb.eng.Engine
import org.vulkanb.eng.IAppLogic
import org.vulkanb.eng.Window
import org.vulkanb.eng.graph.Render
import org.vulkanb.eng.scene.Scene

private val logger = KotlinLogging.logger {}

class Main : IAppLogic {
    override fun cleanup() {
        // To be implemented
    }

    override fun init(window: Window?, scene: Scene?, render: Render?) {
        // To be implemented
    }

    override fun input(window: Window?, scene: Scene?, diffTimeMillis: Long) {
        // To be implemented
    }

    override fun update(window: Window?, scene: Scene?, diffTimeMillis: Long) {
        // To be implemented
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            logger.info { "Starting application" }
            val engine = Engine("Vulkan Book", Main())
            engine.start()
        }
    }
}
