package org.vulkanb.eng.graph;

import org.apache.logging.log4j.*;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.util.*;

public class Render {

    private static final Logger LOGGER = LogManager.getLogger();

    private CommandPool commandPool;
    private Device device;
    private ForwardRenderActivity fwdRenderActivity;
    private Queue.GraphicsQueue graphQueue;
    private Instance instance;
    private List<VulkanMesh> meshList;
    private PhysicalDevice physicalDevice;
    private PipelineCache pipelineCache;
    private Queue.PresentQueue presentQueue;
    private Surface surface;
    private SwapChain swapChain;

    public void cleanUp() {
        this.presentQueue.waitIdle();
        this.graphQueue.waitIdle();
        this.device.waitIdle();
        this.meshList.forEach(VulkanMesh::cleanUp);
        this.pipelineCache.cleanUp();
        this.fwdRenderActivity.cleanUp();
        this.commandPool.cleanUp();
        this.swapChain.cleanUp();
        this.surface.cleanUp();
        this.device.cleanUp();
        this.physicalDevice.cleanUp();
        this.instance.cleanUp();
    }

    public void init(Window window) {
        EngineProperties engProps = EngineProperties.getInstance();
        this.instance = new Instance(engProps.isValidate());
        this.physicalDevice = PhysicalDevice.createPhysicalDevice(this.instance, engProps.getPhysDeviceName());
        this.device = new Device(this.physicalDevice);
        this.surface = new Surface(this.physicalDevice, window.getWindowHandle());
        this.graphQueue = new Queue.GraphicsQueue(this.device, 0);
        this.presentQueue = new Queue.PresentQueue(this.device, this.surface, 0);
        this.swapChain = new SwapChain(this.device, this.surface, window, engProps.getRequestedImages(),
                engProps.isvSync());
        this.commandPool = new CommandPool(this.device, this.graphQueue.getQueueFamilyIndex());
        this.pipelineCache = new PipelineCache(this.device);
        this.fwdRenderActivity = new ForwardRenderActivity(this.swapChain, this.commandPool, this.pipelineCache);
        this.meshList = new ArrayList<>();
    }

    public void loadMeshes(MeshData[] meshDataList) {
        LOGGER.debug("Loading {} meshe(s)", meshDataList.length);
        VulkanMesh[] meshes = VulkanMesh.loadMeshes(this.commandPool, this.graphQueue, meshDataList);
        LOGGER.debug("Loaded {} meshe(s)", meshes.length);
        this.meshList.addAll(Arrays.asList(meshes));
    }

    public void render(Window window, Scene scene) {
        if (window.isResized() || this.swapChain.acquireNextImage()) {
            window.resetResized();
            resize(window);
            scene.getPerspective().resize(window.getWidth(), window.getHeight());
            this.swapChain.acquireNextImage();
        }

        this.fwdRenderActivity.recordCommandBuffers(this.meshList, scene);
        this.fwdRenderActivity.submit(this.presentQueue);

        if (this.swapChain.presentImage(this.graphQueue)) {
            window.setResized(true);
        }
    }

    private void resize(Window window) {
        EngineProperties engProps = EngineProperties.getInstance();

        this.device.waitIdle();
        this.graphQueue.waitIdle();

        this.swapChain.cleanUp();

        this.swapChain = new SwapChain(this.device, this.surface, window, engProps.getRequestedImages(),
                engProps.isvSync());
        this.fwdRenderActivity.resize(this.swapChain);
    }

    public void unloadMesh(String id) {
        this.meshList.removeIf(mesh -> mesh.getId().equals(id));
    }

    public void unloadMeshes() {
        this.device.waitIdle();
        this.meshList.forEach(VulkanMesh::cleanUp);
        this.meshList.clear();
    }
}
