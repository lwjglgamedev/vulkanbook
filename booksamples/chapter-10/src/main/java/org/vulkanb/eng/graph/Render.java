package org.vulkanb.eng.graph;

import org.apache.logging.log4j.*;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.geometry.GeometryRenderActivity;
import org.vulkanb.eng.graph.lighting.LightingRenderActivity;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.util.*;

public class Render {

    private static final Logger LOGGER = LogManager.getLogger();
    private CommandPool commandPool;
    private Device device;
    private GeometryRenderActivity geometryRenderActivity;
    private Queue.GraphicsQueue graphQueue;
    private Instance instance;
    private LightingRenderActivity lightingRenderActivity;
    private List<VulkanMesh> meshList;
    private PhysicalDevice physicalDevice;
    private PipelineCache pipelineCache;
    private Queue.PresentQueue presentQueue;
    private Surface surface;
    private SwapChain swapChain;
    private TextureCache textureCache;

    public void cleanup() {
        presentQueue.waitIdle();
        graphQueue.waitIdle();
        device.waitIdle();
        textureCache.cleanup();
        meshList.forEach(VulkanMesh::cleanup);
        pipelineCache.cleanup();
        lightingRenderActivity.cleanup();
        geometryRenderActivity.cleanup();
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
        textureCache = new TextureCache();
        meshList = new ArrayList<>();
        geometryRenderActivity = new GeometryRenderActivity(swapChain, commandPool, pipelineCache, scene);
        lightingRenderActivity = new LightingRenderActivity(swapChain, commandPool, pipelineCache,
                geometryRenderActivity.getGeometryFrameBuffer(), scene);
    }

    public void loadMeshes(MeshData[] meshDataList) {
        LOGGER.debug("Loading {} meshe(s)", meshDataList.length);
        VulkanMesh[] meshes = VulkanMesh.loadMeshes(textureCache, commandPool, graphQueue, meshDataList);
        LOGGER.debug("Loaded {} meshe(s)", meshes.length);
        meshList.addAll(Arrays.asList(meshes));

        // Reorder meshes
        Collections.sort(meshList, (a, b) -> Boolean.compare(a.getTexture().hasTransparencies(), b.getTexture().hasTransparencies()));

        geometryRenderActivity.meshesLoaded(meshes);
    }

    public void render(Window window, Scene scene) {
        if (window.isResized() || swapChain.acquireNextImage()) {
            window.resetResized();
            resize(window, scene);
            scene.getPerspective().resize(window.getWidth(), window.getHeight());
            swapChain.acquireNextImage();
        }

        geometryRenderActivity.recordCommandBuffers(meshList, scene);
        geometryRenderActivity.submit(graphQueue);
        lightingRenderActivity.recordCommandBuffer(scene);
        lightingRenderActivity.submit(graphQueue);

        if (swapChain.presentImage(graphQueue)) {
            window.setResized(true);
        }
    }

    private void resize(Window window, Scene scene) {
        EngineProperties engProps = EngineProperties.getInstance();

        device.waitIdle();
        graphQueue.waitIdle();

        swapChain.cleanup();

        swapChain = new SwapChain(device, surface, window, engProps.getRequestedImages(),
                engProps.isvSync());
        geometryRenderActivity.resize(swapChain, scene);
        lightingRenderActivity.resize(swapChain, geometryRenderActivity.getGeometryFrameBuffer(), scene);
    }

    public void unloadMesh(String id) {
        Iterator<VulkanMesh> it = meshList.iterator();
        while (it.hasNext()) {
            VulkanMesh mesh = it.next();
            if (mesh.getId().equals(id)) {
                geometryRenderActivity.meshUnLoaded(mesh);
                mesh.cleanup();
                it.remove();
            }
        }
    }

    public void unloadMeshes() {
        device.waitIdle();
        for (VulkanMesh vulkanMesh : meshList) {
            geometryRenderActivity.meshUnLoaded(vulkanMesh);
            vulkanMesh.cleanup();
        }
        meshList.clear();
    }
}
