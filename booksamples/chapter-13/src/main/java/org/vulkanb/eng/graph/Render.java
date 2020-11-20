package org.vulkanb.eng.graph;

import org.apache.logging.log4j.*;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.geometry.GeometryRenderActivity;
import org.vulkanb.eng.graph.lighting.LightingRenderActivity;
import org.vulkanb.eng.graph.shadows.ShadowRenderActivity;
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
    private ShadowRenderActivity shadowRenderActivity;
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
        shadowRenderActivity.cleanup();
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
        device = new Device(instance, physicalDevice);
        surface = new Surface(physicalDevice, window.getWindowHandle());
        graphQueue = new Queue.GraphicsQueue(device, 0);
        presentQueue = new Queue.PresentQueue(device, surface, 0);
        swapChain = new SwapChain(device, surface, window, engProps.getRequestedImages(),
                engProps.isvSync());
        commandPool = new CommandPool(device, graphQueue.getQueueFamilyIndex());
        pipelineCache = new PipelineCache(device);
        meshList = new ArrayList<>();
        textureCache = new TextureCache();
        geometryRenderActivity = new GeometryRenderActivity(swapChain, commandPool, pipelineCache, scene);
        shadowRenderActivity = new ShadowRenderActivity(swapChain, pipelineCache);
        List<Attachment> attachments = new ArrayList<>();
        attachments.addAll(geometryRenderActivity.getAttachments());
        attachments.add(shadowRenderActivity.getAttachment());
        lightingRenderActivity = new LightingRenderActivity(swapChain, commandPool, pipelineCache, attachments);
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
        if (window.getWidth() <= 0 && window.getHeight() <= 0) {
            return;
        }
        if (window.isResized() || swapChain.acquireNextImage()) {
            window.resetResized();
            resize(window, scene);
            scene.getProjection().resize(window.getWidth(), window.getHeight());
            swapChain.acquireNextImage();
        }

        CommandBuffer commandBuffer = geometryRenderActivity.beginRecording();
        geometryRenderActivity.recordCommandBuffers(commandBuffer, meshList, scene);
        shadowRenderActivity.recordCommandBuffers(commandBuffer, meshList, scene);
        geometryRenderActivity.endRecording(commandBuffer);
        geometryRenderActivity.submit(graphQueue);
        lightingRenderActivity.prepareCommandBuffers(scene, shadowRenderActivity.getShadowCascades());
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
        shadowRenderActivity.resize(swapChain, scene);
        List<Attachment> attachments = new ArrayList<>();
        attachments.addAll(geometryRenderActivity.getAttachments());
        attachments.add(shadowRenderActivity.getAttachment());
        lightingRenderActivity.resize(swapChain, attachments, scene);
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
