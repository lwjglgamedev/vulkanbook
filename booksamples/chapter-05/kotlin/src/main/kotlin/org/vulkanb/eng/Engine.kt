package org.vulkanb.eng

import org.vulkanb.eng.graph.Render
import org.vulkanb.eng.scene.Scene

class Engine(windowTitle: String, private val appLogic: IAppLogic) {
    private val render: Render
    private val scene: Scene
    private val window: Window
    private var running = false

    init {
        window = Window(windowTitle)
        scene = Scene(window)
        render = Render(window, scene)
        appLogic.init(window, scene, render)
    }

    private fun cleanup() {
        appLogic.cleanup()
        render.cleanup()
        window.cleanup()
    }

    fun run() {
        var initialTime = System.currentTimeMillis()
        val timeU = 1000.0f / EngineProperties.ups
        var deltaUpdate = 0.0
        var updateTime = initialTime
        while (running && !window.shouldClose()) {
            window.pollEvents()
            val now = System.currentTimeMillis()
            deltaUpdate += ((now - initialTime) / timeU).toDouble()
            appLogic.input(window, scene, now - initialTime)
            if (deltaUpdate >= 1) {
                val diffTimeMilis = now - updateTime
                appLogic.update(window, scene, diffTimeMilis)
                updateTime = now
                deltaUpdate--
            }
            render.render(window, scene)
            initialTime = now
        }
        cleanup()
    }

    fun start() {
        running = true
        run()
    }

    fun stop() {
        running = false
    }
}
