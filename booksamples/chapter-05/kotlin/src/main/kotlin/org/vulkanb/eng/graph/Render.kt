package org.vulkanb.eng.graph

import org.vulkanb.eng.EngineProperties
import org.vulkanb.eng.Window
import org.vulkanb.eng.graph.vk.*
import org.vulkanb.eng.scene.Scene


class Render(window: Window, scene: Scene) {

    private val instance = Instance // force it to initialise
    private val device: Device
    private val graphQueue: Queue.GraphicsQueue
    private val presentQueue: Queue.PresentQueue
    private val physicalDevice: PhysicalDevice
    private val surface: Surface
    private val swapChain: SwapChain
    private val commandPool: CommandPool
    private val fwdRenderActivity: ForwardRenderActivity

    init {
        physicalDevice = PhysicalDevice.createPhysicalDevice(EngineProperties.physDeviceName)
        device = Device(physicalDevice)
        surface = Surface(physicalDevice, window.windowHandle)
        graphQueue = Queue.GraphicsQueue(device, 0)
        presentQueue = Queue.PresentQueue(device, surface, 0)
        swapChain = SwapChain(
            device, surface, window, EngineProperties.requestedImages, EngineProperties.vSync, presentQueue, arrayOf(graphQueue)
        )
        commandPool = CommandPool(device, graphQueue.queueFamilyIndex)
        fwdRenderActivity = ForwardRenderActivity(swapChain, commandPool)
    }

    fun cleanup() {
        presentQueue.waitIdle()
        graphQueue.waitIdle()
        device.waitIdle()
        fwdRenderActivity.cleanup()
        commandPool.cleanup()
        swapChain.cleanup()
        surface.cleanup()
        device.cleanup()
        physicalDevice.cleanup()
        instance.cleanup()
    }

    fun render(window: Window?, scene: Scene?) {
        swapChain.acquireNextImage()

        fwdRenderActivity.submit(graphQueue)

        swapChain.presentImage(presentQueue)
    }
}
