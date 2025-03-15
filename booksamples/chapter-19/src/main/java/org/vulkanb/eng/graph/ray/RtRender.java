package org.vulkanb.eng.graph.ray;

import org.joml.Matrix4f;
import org.lwjgl.system.*;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.*;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.scene.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT;

public class RtRender {
    private static final int COLOR_FORMAT = VK_FORMAT_R8G8B8A8_UNORM;
    private static final String DESC_ID_ACCEL_ST = "RT_DESC_ID_ACCEL_ST";
    private static final String DESC_ID_PRJ = "RT_DESC_ID_PRJ";
    private static final String DESC_ID_ST_IMAGE = "RT_DESC_ID_ST_IMAGE";
    private static final String DESC_ID_ST_MATERIALS = "RT_DESC_ID_ST_MATERIALS";
    private static final String DESC_ID_ST_MESHES_OFFSETS = "RT_DESC_ID_ST_MESHES_OFFSETS";
    private static final String DESC_ID_TEXT = "RT_DESC_ID_TEXT";
    private static final String DESC_ID_VIEW = "RT_DESC_ID_VIEW";

    private static final int MAX_FRAMES = 100;
    private static final int PUSH_CONSTANTS_SIZE = VkUtils.INT_SIZE;
    private static final String RANY_SHADER_FILE_GLSL = "resources/shaders/rany.glsl";
    private static final String RANY_SHADER_FILE_SPV = RANY_SHADER_FILE_GLSL + ".spv";
    private static final String RCLOSE_SHADER_FILE_GLSL = "resources/shaders/rclose.glsl";
    private static final String RCLOSE_SHADER_FILE_SPV = RCLOSE_SHADER_FILE_GLSL + ".spv";
    private static final String RGEN_SHADER_FILE_GLSL = "resources/shaders/rgen.glsl";
    private static final String RGEN_SHADER_FILE_SPV = RGEN_SHADER_FILE_GLSL + ".spv";
    private static final String RMISS_SHADER_FILE_GLSL = "resources/shaders/rmiss.glsl";
    private static final String RMISS_SHADER_FILE_SPV = RMISS_SHADER_FILE_GLSL + ".spv";

    private final Map<String, BLAS> blasMap;
    private final DescSetLayout descLayoutAccelSt;
    private final DescSetLayout descLayoutStBuff;
    private final DescSetLayout descLayoutStImg;
    private final DescSetLayout descLayoutText;
    private final DescSetLayout descLayoutVtxUniform;
    private final RtPipeline pipeline;
    private final Matrix4f prevProjMatrix;
    private final Matrix4f prevViewMatrix;
    private final VkBuffer projMatrixBuff;
    private final ByteBuffer pushConstBuff;
    private final ShaderBindingTables shaderBindingTables;
    private final TextureSampler textureSampler;
    private final VkBuffer[] viewMatricesBuffer;
    private Attachment attColor;
    private VkBuffer buffEntitiesMeshes;
    private int frame;
    private ArrayList<Integer> modelsOffsets;
    private TLAS tlas;

    public RtRender(VkCtx vkCtx, Scene scene) {
        blasMap = new HashMap<>();
        prevViewMatrix = new Matrix4f(scene.getCamera().getViewMatrix());
        prevProjMatrix = new Matrix4f(scene.getProjection().getProjectionMatrix());
        modelsOffsets = new ArrayList<>();

        attColor = createColorAttachment(vkCtx);

        pushConstBuff = MemoryUtil.memAlloc(PUSH_CONSTANTS_SIZE);

        var textureSamplerInfo = new TextureSamplerInfo(VK_SAMPLER_ADDRESS_MODE_REPEAT,
                VK_BORDER_COLOR_INT_OPAQUE_BLACK, 1, true);
        textureSampler = new TextureSampler(vkCtx, textureSamplerInfo);
        descLayoutText = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                0, EngCfg.getInstance().getMaxTextures(), VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR | VK_SHADER_STAGE_ANY_HIT_BIT_KHR));

        descLayoutVtxUniform = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                0, 1, VK_SHADER_STAGE_RAYGEN_BIT_KHR));
        projMatrixBuff = VkUtils.createHostVisibleBuff(vkCtx, VkUtils.MAT4X4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                DESC_ID_PRJ, descLayoutVtxUniform);
        VkUtils.copyMatrixToBuffer(vkCtx, projMatrixBuff, scene.getProjection().getProjectionMatrix(), 0);

        viewMatricesBuffer = VkUtils.createHostVisibleBuffs(vkCtx, VkUtils.MAT4X4_SIZE, VkUtils.MAX_IN_FLIGHT,
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, DESC_ID_VIEW, descLayoutVtxUniform);

        descLayoutAccelSt = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR,
                0, 1, VK_SHADER_STAGE_RAYGEN_BIT_KHR));

        descLayoutStImg = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
                0, 1, VK_SHADER_STAGE_RAYGEN_BIT_KHR));
        createStImageDescSet(vkCtx, descLayoutStImg, attColor);

        descLayoutStBuff = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                0, 1, VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR | VK_SHADER_STAGE_ANY_HIT_BIT_KHR));
        ShaderModule[] shaderModules = createShaderModules(vkCtx);

        VkRayTracingShaderGroupCreateInfoKHR.Buffer groups = createShaderGroups(0, 1, 2, 3);
        pipeline = createPipeline(vkCtx, shaderModules, groups, new DescSetLayout[]{descLayoutVtxUniform,
                descLayoutVtxUniform, descLayoutStImg, descLayoutAccelSt, descLayoutStBuff, descLayoutStBuff, descLayoutText});

        shaderBindingTables = createShaderBindingTables(vkCtx, groups, pipeline);

        Arrays.asList(shaderModules).forEach(s -> s.cleanup(vkCtx));
        groups.free();
        frame = -1;
    }

    private static void createAccelStDescSet(VkCtx vkCtx, DescSetLayout descSetLayout, String id, TLAS tlas) {
        DescAllocator descAllocator = vkCtx.getDescAllocator();
        Device device = vkCtx.getDevice();
        DescSet descSet = descAllocator.addDescSets(device, id, 1, descSetLayout)[0];
        var layoutInfo = descSetLayout.getLayoutInfo();
        descSet.setTLAS(device, layoutInfo.binding(), layoutInfo.descType(), tlas);
    }

    private static Attachment createColorAttachment(VkCtx vkCtx) {
        SwapChain swapChain = vkCtx.getSwapChain();
        VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
        return new Attachment(vkCtx, swapChainExtent.width(), swapChainExtent.height(),
                COLOR_FORMAT, VK_IMAGE_USAGE_STORAGE_BIT, 1);
    }

    private static VkBuffer createMeshesBuffer(VkCtx vkCtx, List<MeshInfo> meshInfoList, CmdPool cmdPool,
                                               Queue queue, DescSetLayout layout) {
        int meshInfoSize = VkUtils.INT_SIZE * 2 + VkUtils.INT64_SIZE * 2;
        int bufferSize = meshInfoList.size() * meshInfoSize;
        var srcBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_SHADER_STAGE_RAYGEN_BIT_KHR | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);

        long mappedMemory = srcBuffer.map(vkCtx);
        ByteBuffer data = MemoryUtil.memByteBuffer(mappedMemory, bufferSize);
        int offset = 0;
        for (MeshInfo meshInfo : meshInfoList) {
            data.putInt(offset, meshInfo.materialIdx());
            // Padding
            data.putInt(offset + VkUtils.INT_SIZE, 0);
            data.putLong(offset + VkUtils.INT_SIZE * 2, meshInfo.vtxAddress());
            data.putLong(offset + VkUtils.INT_SIZE * 2 + VkUtils.INT64_SIZE, meshInfo.idxAddress());
            offset += meshInfoSize;
        }
        srcBuffer.unMap(vkCtx);

        var cmd = new CmdBuffer(vkCtx, cmdPool, true, true);
        cmd.beginRecording();

        try (var stack = MemoryStack.stackPush()) {
            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack)
                    .srcOffset(0).dstOffset(0).size(srcBuffer.getRequestedSize());
            vkCmdCopyBuffer(cmd.getVkCommandBuffer(), srcBuffer.getBuffer(), dstBuffer.getBuffer(), copyRegion);
        }

        cmd.endRecording();
        cmd.submitAndWait(vkCtx, queue);

        srcBuffer.cleanup(vkCtx);

        DescAllocator descAllocator = vkCtx.getDescAllocator();
        Device device = vkCtx.getDevice();
        DescSet descSet = descAllocator.addDescSets(device, DESC_ID_ST_MESHES_OFFSETS, 1, layout)[0];
        descSet.setBuffer(device, dstBuffer, dstBuffer.getRequestedSize(), layout.getLayoutInfo().binding(),
                layout.getLayoutInfo().descType());

        return dstBuffer;
    }

    private static RtPipeline createPipeline(VkCtx vkCtx, ShaderModule[] shaderModules,
                                             VkRayTracingShaderGroupCreateInfoKHR.Buffer groups,
                                             DescSetLayout[] descSetLayouts) {
        var buildInfo = new RtPipelineBuildInfo(shaderModules, groups)
                .setPushConstRanges(
                        new PushConstRange[]{
                                new PushConstRange(VK_SHADER_STAGE_RAYGEN_BIT_KHR, 0, PUSH_CONSTANTS_SIZE),
                        })
                .setDescSetLayouts(descSetLayouts);
        return new RtPipeline(vkCtx, buildInfo);
    }

    private static ShaderBindingTables createShaderBindingTables(VkCtx vkCtx, VkRayTracingShaderGroupCreateInfoKHR.Buffer shaderGroups,
                                                                 RtPipeline pipeline) {
        ShaderBindingTables shaderBindingTables;
        try (var stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceRayTracingPipelinePropertiesKHR props = vkCtx.getPhysDevice().getRayTracingProperties();
            int handleSize = props.shaderGroupHandleSize();
            int shaderGroupHandleSize = props.shaderGroupHandleSize();
            int shaderGroupHandleAlignment = props.shaderGroupHandleAlignment();
            int handleSizeAligned = VkUtils.alignUp(shaderGroupHandleSize, shaderGroupHandleAlignment);
            int groupCount = shaderGroups.remaining();
            int sbtSize = groupCount * handleSizeAligned;

            var dataBuff = stack.calloc(sbtSize);

            vkGetRayTracingShaderGroupHandlesKHR(vkCtx.getDevice().getVkDevice(), pipeline.getVkPipeline(), 0,
                    groupCount, dataBuff);

            shaderBindingTables = new ShaderBindingTables(
                    new ShaderBindingTable(vkCtx, 1),
                    new ShaderBindingTable(vkCtx, 1),
                    new ShaderBindingTable(vkCtx, 1)
            );

            // Copy handles
            int offset = 0;
            VkUtils.copyBufferToBuffer(vkCtx, dataBuff, offset, shaderBindingTables.rayGen().getBuffer(), 0, handleSize);
            offset += handleSizeAligned;
            VkUtils.copyBufferToBuffer(vkCtx, dataBuff, offset, shaderBindingTables.miss().getBuffer(), 0, handleSize);
            offset += handleSizeAligned;
            VkUtils.copyBufferToBuffer(vkCtx, dataBuff, offset, shaderBindingTables.hit().getBuffer(), 0, handleSize);
        }
        return shaderBindingTables;
    }

    private static VkRayTracingShaderGroupCreateInfoKHR.Buffer createShaderGroups(int rgenModulePos, int missModulePos,
                                                                                  int hitModulePos, int anyHitModulePos) {
        int numGroups = 3;
        var groups = VkRayTracingShaderGroupCreateInfoKHR.calloc(numGroups);
        // Ray Generation Group
        groups.get(0)
                .sType$Default()
                .type(VK_RAY_TRACING_SHADER_GROUP_TYPE_GENERAL_KHR)
                .generalShader(rgenModulePos)
                .closestHitShader(VK_SHADER_UNUSED_KHR)
                .anyHitShader(VK_SHADER_UNUSED_KHR)
                .intersectionShader(VK_SHADER_UNUSED_KHR);
        // Miss Group
        groups.get(1)
                .sType$Default()
                .type(VK_RAY_TRACING_SHADER_GROUP_TYPE_GENERAL_KHR)
                .generalShader(missModulePos)
                .closestHitShader(VK_SHADER_UNUSED_KHR)
                .anyHitShader(VK_SHADER_UNUSED_KHR)
                .intersectionShader(VK_SHADER_UNUSED_KHR);
        // Closes hit group
        groups.get(2)
                .sType$Default()
                .type(VK_RAY_TRACING_SHADER_GROUP_TYPE_TRIANGLES_HIT_GROUP_KHR)
                .generalShader(VK_SHADER_UNUSED_KHR)
                .closestHitShader(hitModulePos)
                .anyHitShader(anyHitModulePos)
                .intersectionShader(VK_SHADER_UNUSED_KHR);
        return groups;
    }

    private static ShaderModule[] createShaderModules(VkCtx vkCtx) {
        if (EngCfg.getInstance().isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(RGEN_SHADER_FILE_GLSL, Shaderc.shaderc_raygen_shader);
            ShaderCompiler.compileShaderIfChanged(RMISS_SHADER_FILE_GLSL, Shaderc.shaderc_miss_shader);
            ShaderCompiler.compileShaderIfChanged(RCLOSE_SHADER_FILE_GLSL, Shaderc.shaderc_closesthit_shader);
            ShaderCompiler.compileShaderIfChanged(RANY_SHADER_FILE_GLSL, Shaderc.shaderc_anyhit_shader);
        }
        return new ShaderModule[]{
                new ShaderModule(vkCtx, VK_SHADER_STAGE_RAYGEN_BIT_KHR, RGEN_SHADER_FILE_SPV, null),
                new ShaderModule(vkCtx, VK_SHADER_STAGE_MISS_BIT_KHR, RMISS_SHADER_FILE_SPV, null),
                new ShaderModule(vkCtx, VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR, RCLOSE_SHADER_FILE_SPV, null),
                new ShaderModule(vkCtx, VK_SHADER_STAGE_ANY_HIT_BIT_KHR, RANY_SHADER_FILE_SPV, null),
        };
    }

    private static void createStImageDescSet(VkCtx vkCtx, DescSetLayout descSetLayout, Attachment attachment) {
        DescAllocator descAllocator = vkCtx.getDescAllocator();
        Device device = vkCtx.getDevice();
        DescSet descSet = descAllocator.addDescSets(device, DESC_ID_ST_IMAGE, 1, descSetLayout)[0];
        descSet.setImage(device, attachment.getImageView(), null, 0);
    }

    public void cleanup(VkCtx vkCtx) {
        attColor.cleanup(vkCtx);
        MemoryUtil.memFree(pushConstBuff);
        pipeline.cleanup(vkCtx);
        textureSampler.cleanup(vkCtx);
        descLayoutText.cleanup(vkCtx);
        descLayoutAccelSt.cleanup(vkCtx);
        descLayoutStImg.cleanup(vkCtx);
        descLayoutVtxUniform.cleanup(vkCtx);
        descLayoutStBuff.cleanup(vkCtx);
        tlas.cleanup(vkCtx);
        blasMap.values().forEach(t -> t.cleanup(vkCtx));
        projMatrixBuff.cleanup(vkCtx);
        Arrays.asList(viewMatricesBuffer).forEach(b -> b.cleanup(vkCtx));
        shaderBindingTables.cleanup(vkCtx);
        buffEntitiesMeshes.cleanup(vkCtx);
    }

    public Attachment getAttColor() {
        return attColor;
    }

    public void loadMaterials(VkCtx vkCtx, MaterialsCache materialsCache, TextureCache textureCache) {
        DescAllocator descAllocator = vkCtx.getDescAllocator();
        Device device = vkCtx.getDevice();
        DescSet descSet = descAllocator.addDescSet(device, DESC_ID_ST_MATERIALS, descLayoutStBuff);
        DescSetLayout.LayoutInfo layoutInfo = descLayoutStBuff.getLayoutInfo();
        var buffer = materialsCache.getMaterialsBuffer();
        descSet.setBuffer(device, buffer, buffer.getRequestedSize(), layoutInfo.binding(), layoutInfo.descType());

        List<ImageView> imageViews = textureCache.getAsList().stream().map(Texture::getImageView).toList();
        descSet = vkCtx.getDescAllocator().addDescSet(device, DESC_ID_TEXT, descLayoutText);
        descSet.setImagesArr(device, imageViews, textureSampler, 0);
    }

    public void loadModels(EngCtx engCtx, VkCtx vkCtx, ModelsCache modelsCache, MaterialsCache materialsCache,
                           CmdPool cmdPool, Queue queue) {

        var modelOffsetMap = new HashMap<String, Integer>();
        Map<String, VulkanModel> modelsMap = modelsCache.getModelsMap();
        int offsetModel = 0;
        for (var modelEntry : modelsMap.entrySet()) {
            VulkanModel vulkanModel = modelEntry.getValue();
            BLAS blas = new BLAS(vkCtx, vulkanModel, cmdPool, queue);
            blasMap.put(modelEntry.getKey(), blas);
            modelOffsetMap.put(modelEntry.getKey(), offsetModel);
            offsetModel += vulkanModel.getVulkanMeshList().size();
        }

        try (var stack = MemoryStack.stackPush()) {
            List<MeshInfo> meshInfoList = new ArrayList<>();
            List<Entity> entities = engCtx.scene().getEntities();
            int numEntities = entities.size();
            modelsOffsets = new ArrayList<Integer>();

            for (int i = 0; i < numEntities; i++) {
                Entity entity = entities.get(i);
                VulkanModel vulkanModel = modelsMap.get(entity.getModelId());
                List<VulkanMesh> meshList = vulkanModel.getVulkanMeshList();
                int numMeshes = meshList.size();
                for (int j = 0; j < numMeshes; j++) {
                    VulkanMesh mesh = meshList.get(j);
                    int materialIdx = materialsCache.getPosition(mesh.materialdId());
                    var vtxBuffAddress = VkUtils.getBufferAddressConst(stack, vkCtx, mesh.verticesBuffer().getBuffer());
                    var idxBuffAddress = VkUtils.getBufferAddressConst(stack, vkCtx, mesh.indicesBuffer().getBuffer());
                    meshInfoList.add(new MeshInfo(materialIdx, vtxBuffAddress.deviceAddress(), idxBuffAddress.deviceAddress()));
                }
                modelsOffsets.add(modelOffsetMap.get(vulkanModel.getId()));
            }

            tlas = new TLAS(vkCtx, entities, blasMap, modelsOffsets, cmdPool, queue);
            createAccelStDescSet(vkCtx, descLayoutAccelSt, DESC_ID_ACCEL_ST, tlas);

            buffEntitiesMeshes = createMeshesBuffer(vkCtx, meshInfoList, cmdPool, queue, descLayoutStBuff);
        }
    }

    public void render(EngCtx engCtx, VkCtx vkCtx, CmdPool cmdPool, Queue queue, CmdBuffer cmdBuffer, int currentFrame) {
        try (var stack = MemoryStack.stackPush()) {
            Scene scene = engCtx.scene();
            updateFrame(vkCtx, scene, cmdPool, queue);
            if (frame >= MAX_FRAMES) {
                return;
            }
            int width = attColor.getImage().getWidth();
            int height = attColor.getImage().getHeight();

            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            VkUtils.imageBarrier(stack, cmdHandle, attColor.getImage().getVkImage(),
                    VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_GENERAL,
                    VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR,
                    VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT,
                    VK_IMAGE_ASPECT_COLOR_BIT);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR, pipeline.getVkPipeline());
            DescAllocator descAllocator = vkCtx.getDescAllocator();
            LongBuffer descriptorSets = stack.mallocLong(7)
                    .put(0, descAllocator.getDescSet(DESC_ID_PRJ).getVkDescriptorSet())
                    .put(1, descAllocator.getDescSet(DESC_ID_VIEW, currentFrame).getVkDescriptorSet())
                    .put(2, descAllocator.getDescSet(DESC_ID_ST_IMAGE).getVkDescriptorSet())
                    .put(3, descAllocator.getDescSet(DESC_ID_ACCEL_ST).getVkDescriptorSet())
                    .put(4, descAllocator.getDescSet(DESC_ID_ST_MESHES_OFFSETS).getVkDescriptorSet())
                    .put(5, descAllocator.getDescSet(DESC_ID_ST_MATERIALS).getVkDescriptorSet())
                    .put(6, descAllocator.getDescSet(DESC_ID_TEXT).getVkDescriptorSet());

            VkUtils.copyMatrixToBuffer(vkCtx, viewMatricesBuffer[currentFrame], scene.getCamera().getViewMatrix(), 0);

            setPushConstants(cmdHandle);

            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR, pipeline.getVkPipelineLayout(),
                    0, descriptorSets, null);

            vkCmdTraceRaysKHR(cmdHandle,
                    shaderBindingTables.rayGen().getStridedDeviceAddressRegionKHR(),
                    shaderBindingTables.miss().getStridedDeviceAddressRegionKHR(),
                    shaderBindingTables.hit().getStridedDeviceAddressRegionKHR(),
                    VkStridedDeviceAddressRegionKHR.calloc(stack), width, height, 1);
        }
    }

    public void resize(VkCtx vkCtx, EngCtx engCtx) {
        attColor.cleanup(vkCtx);
        vkCtx.getDescAllocator().freeDescSet(vkCtx.getDevice(), DESC_ID_ST_IMAGE);

        attColor = createColorAttachment(vkCtx);
        createStImageDescSet(vkCtx, descLayoutStImg, attColor);
        VkUtils.copyMatrixToBuffer(vkCtx, projMatrixBuff, engCtx.scene().getProjection().getProjectionMatrix(), 0);
    }

    private void setPushConstants(VkCommandBuffer cmdHandle) {
        pushConstBuff.putInt(0, frame);
        vkCmdPushConstants(cmdHandle, pipeline.getVkPipelineLayout(), VK_SHADER_STAGE_RAYGEN_BIT_KHR, 0, pushConstBuff);
    }

    private void updateFrame(VkCtx vkCtx, Scene scene, CmdPool cmdPool, Queue queue) {
        boolean changed = false;
        Matrix4f viewMatrix = scene.getCamera().getViewMatrix();
        Matrix4f projMatrix = scene.getProjection().getProjectionMatrix();
        if (!viewMatrix.equals(prevViewMatrix)) {
            prevViewMatrix.set(viewMatrix);
            changed = true;
        }
        if (!changed && !projMatrix.equals(prevProjMatrix)) {
            prevProjMatrix.set(projMatrix);
            changed = true;
        }
        if (!changed) {
            changed = tlas.update(vkCtx, scene.getEntities(), cmdPool, queue);
        }
        if (changed) {
            frame = -1;
        }
        frame++;
    }
}
