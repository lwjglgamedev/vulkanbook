package org.vulkanb.eng

import org.vulkanb.eng.graph.Render
import org.vulkanb.eng.scene.Scene

interface IAppLogic {
    fun cleanup()
    fun input(window: Window?, scene: Scene?, diffTimeMillis: Long)
    fun init(window: Window?, scene: Scene?, render: Render?)
    fun update(window: Window?, scene: Scene?, diffTimeMillis: Long)
}
