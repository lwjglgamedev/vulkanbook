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

    public void cleanup() {
        presentQueue.waitIdle();
        graphQueue.waitIdle();
        device.waitIdle();
        meshList.forEach(VulkanMesh::cleanup);
        pipelineCache.cleanup();
        fwdRenderActivity.cleanup();
        commandPool.cleanup();
        swapChain.cleanup();
        surface.cleanup();
        device.cleanup();
        physicalDevice.cleanup();
        instance.cleanup();
    }

    public void init(Window window, Scene scene) {
        EngineProperties engProps = EngineProperties.getInstance();
        instance = new Instance(engProps.isValidate());
        physicalDevice = PhysicalDevice.createPhysicalDevice(instance, engProps.getPhysDeviceName());
        device = new Device(physicalDevice);
        surface = new Surface(physicalDevice, window.getWindowHandle());
        graphQueue = new Queue.GraphicsQueue(device, 0);
        presentQueue = new Queue.PresentQueue(device, surface, 0);
        swapChain = new SwapChain(device, surface, window, engProps.getRequestedImages(),
                engProps.isvSync());
        commandPool = new CommandPool(device, graphQueue.getQueueFamilyIndex());
        pipelineCache = new PipelineCache(device);
        fwdRenderActivity = new ForwardRenderActivity(swapChain, commandPool, pipelineCache);
        meshList = new ArrayList<>();
    }

    public void loadMeshes(MeshData[] meshDataList) {
        LOGGER.debug("Loading {} meshe(s)", meshDataList.length);
        VulkanMesh[] meshes = VulkanMesh.loadMeshes(commandPool, graphQueue, meshDataList);
        LOGGER.debug("Loaded {} meshe(s)", meshes.length);
        meshList.addAll(Arrays.asList(meshes));
    }

    public void render(Window window, Scene scene) {
        swapChain.acquireNextImage();

        fwdRenderActivity.recordCommandBuffers(meshList);
        fwdRenderActivity.submit(presentQueue);

        swapChain.presentImage(graphQueue);
    }

    public void unloadMesh(String id) {
        Iterator<VulkanMesh> it = meshList.iterator();
        while (it.hasNext()) {
            VulkanMesh mesh = it.next();
            if (mesh.getId().equals(id)) {
                mesh.cleanup();
                it.remove();
            }
        }
    }

    public void unloadMeshes() {
        device.waitIdle();
        for (VulkanMesh vulkanMesh : meshList) {
            vulkanMesh.cleanup();
        }
        meshList.clear();
    }
}
