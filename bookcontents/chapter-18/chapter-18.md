# Chapter 18 - Buffer Device Address (BDA)

In this chapter we will use Buffer device address, which will allow us to refer to buffer virtual addresses in shaders instead of using descriptor sets. This is very convenient
to use a bind-less approach where we will access data  as pointers, and will simplify the code a lot. The drawback is that any miss-use in setting the addresses may cause the GPU to crash.

You can find the complete source code for this chapter [here](../../booksamples/chapter-18).


## Enable BDA

First we need to enable Buffer Device Address (BDA) feature in the `Device` class. Since we will be passing pointers to the shaders, which are 8 bytes long, we need also to enable `int64` feature:

```java
public class Device {
    ...
    public Device(PhysDevice physDevice) {
        ...
            var features12 = VkPhysicalDeviceVulkan12Features.calloc(stack)
                    .sType$Default()
                    .bufferDeviceAddress(true)
                    .scalarBlockLayout(true);
            ...
            features.shaderInt64(true);
            ...
        ...
    }
    ...
}
```

In order to get a device address from a buffer we need to use the `VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT` flag, in the `bufferUsage` constructor parameter. If a Vulkan
buffer is created with that flag, we can store the address in a class attribute:

```java
public class VkBuffer {
    ...
    private Long address;
    ...
    public VkBuffer(VkCtx vkCtx, long size, int bufferUsage, int vmaUsage, int vmaFlags, int reqFlags) {
        ...
        ...
            if ((bufferUsage & VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT) > 0) {
                address = VkUtils.getBufferAddress(vkCtx, buffer);
            }
    }
    ...
}
```

The method to get the buffer device address is define din the `VkUtils` class, which also defines a constant to define the size of bytes of buffer memory addresses (8 bytes).
To get the address we just call the `vkGetBufferDeviceAddress` over a buffer handle:

```java
public class VkUtils {
    ...
    public static final int PTR_SIZE = 8;
    ...
    public static long getBufferAddress(VkCtx vkCtx, long buffer) {
        long address;
        try (var stack = MemoryStack.stackPush()) {
            address = vkGetBufferDeviceAddress(vkCtx.getDevice().getVkDevice(), VkBufferDeviceAddressInfo
                    .calloc(stack)
                    .sType$Default()
                    .buffer(buffer));
        }
        return address;
    }
    ...
}
```

Back to the `VkBuffer` we will provide a *getter* that will return the address. If the buffer has not been created with the proper flag, it will raise an exception:

```java
public class VkBuffer {
    ...
    public long getAddress() {
        if (address == null) {
            throw new IllegalStateException("Buffer was not created with VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT usage flag");
        }
        return address;
    }
    ...
}
```

Since we are allocating memory with [VMA](https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator), we need to enable that feature in VMA allocator suing the
`VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT` flag:

```java
public class MemAlloc {
    ...
    public MemAlloc(Instance instance, PhysDevice physDevice, Device device) {
        ...
            var createInfo = VmaAllocatorCreateInfo.calloc(stack)
                    .flags(VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT)
        ...
    }
    ...
}
```

## Scene render changes

Now we are ready to start using BDAs in our render stages, let's start with the scene render one. In order to understand the changes that need to be done, we will examine
first the vertex shade. This will help us to understand how BDA works from the GPU side and the changes that are required in the Java Code. The vertex shader (`scn_vtx.glsl`)
starts like this:

```glsl
#version 450
#extension GL_EXT_buffer_reference: require
#extension GL_EXT_buffer_reference2: enable
#extension GL_EXT_scalar_block_layout: require
...
```
We first use several extensions required to use buffer addresses and to use scalar layouts for them. The next part is quite similar to the ones used before. We just define
output values and the uniforms we will use to store projection and view matrices.

```glsl
...
layout(location = 0) out vec4 outPos;
layout(location = 1) out vec3 outNormal;
layout(location = 2) out vec3 outTangent;
layout(location = 3) out vec3 outBitangent;
layout(location = 4) out vec2 outTextCoords;

layout(set = 0, binding = 0) uniform ProjUniform {
    mat4 matrix;
} projUniform;
layout(set = 1, binding = 0) uniform ViewUniform {
    mat4 matrix;
} viewUniform;
...
```

You may have noticed that we do not define the structure of vertex input attributes. We will not need that. Vertices information will be passed to the shader as a buffer
reference (like a pointer). We can define the internal structure if that buffer as a GLS struct, like this:

```glsl
...
struct Vertex {
    vec3 inPos;
    vec3 inNormal;
    vec3 inTangent;
    vec3 inBitangent;
    vec2 inTextCoords;
};

layout(scalar, buffer_reference) buffer VertexBuffer {
    Vertex[] vertices;
};

layout(std430, buffer_reference) buffer IndexBuffer {
    uint[] indices;
};
...
```

We define two buffer references:
- VertexBuffer, which will contain vertices data.
- IndexBuffer, which will contain the indices to be drawn.

For the `VertexBuffer` we define a `Vertex` structure which defines how the data is organized in the buffer reference. The code continues like this:

```glsl
...
layout(push_constant) uniform pc {
    mat4 modelMatrix;
    VertexBuffer vertexBuffer;
    IndexBuffer indexBuffer;
} push_constants;

void main()
{
    uint index = push_constants.indexBuffer.indices[gl_VertexIndex];
    VertexBuffer vertexData = push_constants.vertexBuffer;

    Vertex vertex = vertexData.vertices[index];
    vec3 inPos    = vertex.inPos;
    vec4 worldPos = push_constants.modelMatrix * vec4(inPos, 1);
    gl_Position   = projUniform.matrix * viewUniform.matrix * worldPos;
    mat3 mNormal  = transpose(inverse(mat3(push_constants.modelMatrix)));

    outPos        = worldPos;
    outNormal     = mNormal * normalize(vertex.inNormal);
    outTangent    = mNormal * normalize(vertex.inTangent);
    outBitangent  = mNormal * normalize(vertex.inBitangent);

    outTextCoords = vertex.inTextCoords;
}
```

In the push constants, in addition to the model matrix, we are passing the addresses of the vertices and indices buffers. We can access those buffers, and access the 
specific index by using the `gl_VertexIndex` built-in variable. This variable will contain  the index of the current vertex being processed by the vertex shader. With that
value, we can get the specific vertex associated to that index and process vertices data as in previous examples.

Since we have modified vertex push constants, we need to update the offset associated to the push constants data used in the fragment shader (`scn_frg.glsl`):

```glsl
...
layout(push_constant) uniform pc {
    layout(offset = 64) uint materialIdx;
} push_constants;
...
```

We will update now the `ScnRender` class to adapt it to the changes in the shader:

```java
public class ScnRender {
    ...
    private static final int PUSH_CONSTANTS_SIZE = VkUtils.MAT4X4_SIZE + VkUtils.PTR_SIZE * 2 + VkUtils.INT_SIZE;
    ...
    private static Pipeline createPipeline(VkCtx vkCtx, ShaderModule[] shaderModules, DescSetLayout[] descSetLayouts) {
        var vtxBuffStruct = new EmptyVtxBuffStruct();
        int vtxPcSize = VkUtils.MAT4X4_SIZE + VkUtils.PTR_SIZE * 2;
        var buildInfo = new PipelineBuildInfo(shaderModules, vtxBuffStruct.getVi(), new int[]{
                MrtAttachments.POSITION_FORMAT, MrtAttachments.ALBEDO_FORMAT, MrtAttachments.NORMAL_FORMAT, MrtAttachments.PBR_FORMAT})
                .setDepthFormat(MrtAttachments.DEPTH_FORMAT)
                .setPushConstRanges(
                        new PushConstRange[]{
                                new PushConstRange(VK_SHADER_STAGE_VERTEX_BIT, 0, vtxPcSize),
                                new PushConstRange(VK_SHADER_STAGE_FRAGMENT_BIT, vtxPcSize, VkUtils.INT_SIZE),
                        })
                .setDescSetLayouts(descSetLayouts)
                .setUseBlend(true);
        ...
    }
    ...
}
```

The size of the push constants has been changed, therefore we need to update the `PUSH_CONSTANTS_SIZE` constant and the size and offset associated to the push constants
used in vertex and fragment shader. We do not need now to define the structure of the vertices information when creating the pipeline, this is already defined in the
vertex shader (through the `Vertex` struct). Therefore, we will use the class `EmptyVtxBuffStruct` to model an empty place holder. Therefore, the `VtxBuffStruct` will
no longer be required. The biggest change, however, is in the `renderEntities` method, which is now defined like this:

```java
public class ScnRender {
    ...
    private void renderEntities(EngCtx engCtx, VkCommandBuffer cmdHandle, ModelsCache modelsCache,
                                MaterialsCache materialsCache, AnimationsCache animationsCache, boolean transparent) {
        Scene scene = engCtx.scene();
        List<Entity> entities = scene.getEntities();
        int numEntities = entities.size();
        for (int i = 0; i < numEntities; i++) {
            var entity = entities.get(i);
            VulkanModel model = modelsCache.getModel(entity.getModelId());
            List<VulkanMesh> vulkanMeshList = model.getVulkanMeshList();
            int numMeshes = vulkanMeshList.size();
            for (int j = 0; j < numMeshes; j++) {
                var vulkanMesh = vulkanMeshList.get(j);
                String materialId = vulkanMesh.materialdId();
                int materialIdx = materialsCache.getPosition(materialId);
                VulkanMaterial vulkanMaterial = materialsCache.getMaterial(materialId);
                if (vulkanMaterial == null) {
                    Logger.warn("Mesh [{}] in model [{}] does not have material", j, model.getId());
                    continue;
                }
                if (vulkanMaterial.isTransparent() == transparent) {
                    long bufferAddress = model.hasAnimations() ?
                            animationsCache.getBuffer(entity.getId(), vulkanMesh.id()).getAddress() :
                            vulkanMesh.verticesBuffer().getAddress();
                    setPushConstants(cmdHandle, entity.getModelMatrix(), bufferAddress, vulkanMesh.indicesBuffer().getAddress(),
                            materialIdx);
                    vkCmdDraw(cmdHandle, vulkanMesh.numIndices(), 1, 0, 0);
                }
            }
        }
    }
    ...
}
```

As you can see there are no calls to `vkCmdBindVertexBuffers` and `vkCmdBindIndexBuffer` functions. Drawing is triggered by calling `vkCmdDraw` instead of calling
`vkCmdDrawIndexed`. The `setPushConstants` needs also to be updated to pass the addresses of the buffers as push constants, instead of relying on binding them:

```java
public class ScnRender {
    ...
    private void setPushConstants(VkCommandBuffer cmdHandle, Matrix4f modelMatrix, int materialIdx) {
        modelMatrix.get(0, pushConstBuff);
        pushConstBuff.putInt(VkUtils.MAT4X4_SIZE, materialIdx);
        vkCmdPushConstants(cmdHandle, pipeline.getVkPipelineLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0,
                pushConstBuff.slice(0, VkUtils.MAT4X4_SIZE));
        vkCmdPushConstants(cmdHandle, pipeline.getVkPipelineLayout(), VK_SHADER_STAGE_FRAGMENT_BIT, VkUtils.MAT4X4_SIZE,
                pushConstBuff.slice(VkUtils.MAT4X4_SIZE, VkUtils.INT_SIZE));
    }
    ...
}
```

## Shadow render changes

Changes in the shadow render stage are quite similar. We need to update the vertex shader (`shadow_vtx.glsl`) to use buffer references:

```glsl
#version 450
#extension GL_EXT_buffer_reference: require
#extension GL_EXT_buffer_reference2: enable
#extension GL_EXT_scalar_block_layout: require

struct Vertex {
    vec3 inPos;
    vec3 inNormal;
    vec3 inTangent;
    vec3 inBitangent;
    vec2 inTextCoords;
};

layout(scalar, buffer_reference) buffer VertexBuffer {
    Vertex[] vertices;
};

layout(std430, buffer_reference) buffer IndexBuffer {
    uint[] indices;
};

struct InstanceData {
    mat4 modelMatrix;
    uint materialIdx;
    uint padding[3];
};

layout(push_constant) uniform pc {
    mat4 modelMatrix;
    VertexBuffer vertexBuffer;
    IndexBuffer indexBuffer;
    uint materialIdx;
} push_constants;

layout (location = 0) out vec2 outTextCoords;
layout (location = 1) out flat uint outMaterialIdx;

void main()
{
    uint index = push_constants.indexBuffer.indices[gl_VertexIndex];
    VertexBuffer vertexData = push_constants.vertexBuffer;

    Vertex vertex = vertexData.vertices[index];

    outTextCoords  = vertex.inTextCoords;
    outMaterialIdx = push_constants.materialIdx;

    gl_Position = push_constants.modelMatrix * vec4(vertex.inPos, 1.0f);
}
```

We need also to modify the `ShadowRender` class in the same way as the `ScnRender` one:

```java
public class ShadowRender {
    ...
    private static final int PUSH_CONSTANTS_SIZE = VkUtils.MAT4X4_SIZE + VkUtils.PTR_SIZE * 2 + VkUtils.INT_SIZE;
    ...
    private static Pipeline createPipeline(VkCtx vkCtx, ShaderModule[] shaderModules, DescSetLayout[] descSetLayouts) {
        var vtxBuffStruct = new EmptyVtxBuffStruct();
        ...
    }
    ...
    public void render(EngCtx engCtx, VkCtx vkCtx, CmdBuffer cmdBuffer, ModelsCache modelsCache,
                       MaterialsCache materialsCache, AnimationsCache animationsCache, int currentFrame) {
        try (var stack = MemoryStack.stackPush()) {
            Scene scene = engCtx.scene();

            ShadowUtils.updateCascadeShadows(cascadeShadows[currentFrame], scene);

            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            VkUtils.imageBarrier(stack, cmdHandle, colorAttachment.getImage().getVkImage(),
                    VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_NONE, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_ASPECT_COLOR_BIT);

            vkCmdBeginRendering(cmdHandle, renderingInfo);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipeline());

            int shadowMapSize = EngCfg.getInstance().getShadowMapSize();
            int width = shadowMapSize;
            int height = shadowMapSize;
            var viewport = VkViewport.calloc(1, stack)
                    .x(0)
                    .y(height)
                    .height(-height)
                    .width(width)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(cmdHandle, 0, viewport);

            var scissor = VkRect2D.calloc(1, stack)
                    .extent(it -> it.width(width).height(height))
                    .offset(it -> it.x(0).y(0));
            vkCmdSetScissor(cmdHandle, 0, scissor);

            updateProjBuffer(vkCtx, currentFrame);
            DescAllocator descAllocator = vkCtx.getDescAllocator();
            LongBuffer descriptorSets = stack.mallocLong(3)
                    .put(0, descAllocator.getDescSet(DESC_ID_PRJ, currentFrame).getVkDescriptorSet())
                    .put(1, descAllocator.getDescSet(DESC_ID_TEXT).getVkDescriptorSet())
                    .put(2, descAllocator.getDescSet(DESC_ID_MAT).getVkDescriptorSet());
            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipelineLayout(),
                    0, descriptorSets, null);

            List<Entity> entities = scene.getEntities();
            int numEntities = entities.size();
            for (int i = 0; i < numEntities; i++) {
                var entity = entities.get(i);
                VulkanModel model = modelsCache.getModel(entity.getModelId());
                List<VulkanMesh> vulkanMeshList = model.getVulkanMeshList();
                int numMeshes = vulkanMeshList.size();
                for (int j = 0; j < numMeshes; j++) {
                    var vulkanMesh = vulkanMeshList.get(j);
                    String materialId = vulkanMesh.materialdId();
                    int materialIdx = materialsCache.getPosition(materialId);
                    VulkanMaterial vulkanMaterial = materialsCache.getMaterial(materialId);
                    if (vulkanMaterial == null) {
                        Logger.warn("Mesh [{}] in model [{}] does not have material", j, model.getId());
                        continue;
                    }
                    long bufferAddress = model.hasAnimations() ?
                            animationsCache.getBuffer(entity.getId(), vulkanMesh.id()).getAddress() :
                            vulkanMesh.verticesBuffer().getAddress();
                    setPushConstants(cmdHandle, entity.getModelMatrix(), bufferAddress, vulkanMesh.indicesBuffer().getAddress(),
                            materialIdx);

                    vkCmdDraw(cmdHandle, vulkanMesh.numIndices(), 1, 0, 0);
                }
            }

            vkCmdEndRendering(cmdHandle);
        }
    }

    private void setPushConstants(VkCommandBuffer cmdHandle, Matrix4f modelMatrix, long vtxAddress,
                                  long indicesAddress, int materialIdx) {
        int vtxPcSize = VkUtils.MAT4X4_SIZE + VkUtils.PTR_SIZE * 2;
        modelMatrix.get(0, pushConstBuff);
        pushConstBuff.putLong(VkUtils.MAT4X4_SIZE, vtxAddress);
        pushConstBuff.putLong(VkUtils.MAT4X4_SIZE + VkUtils.PTR_SIZE, indicesAddress);
        pushConstBuff.putInt(vtxPcSize, materialIdx);
        vkCmdPushConstants(cmdHandle, pipeline.getVkPipelineLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstBuff);
    }
    ...
}
```

## Animation render changes

We will also buffer references in the animation compute shader (`anim_comp.glsl`):

```glsl
#version 450
#extension GL_EXT_buffer_reference: require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : enable

layout(std430, buffer_reference) buffer FloatBuf {
    float data[];
};

layout(std430, buffer_reference) buffer MatricesBuf {
    mat4[] data;
};

layout (local_size_x=32, local_size_y=1, local_size_z=1) in;

layout(push_constant) uniform Pc {
    FloatBuf srcBuf;
    FloatBuf weightsBuf;
    MatricesBuf jointsBuf;
    FloatBuf dstBuf;
    uint64_t srcBuffFloatSize;
} pc;


void main()
{
    int baseIdxSrcBuf = int(gl_GlobalInvocationID.x) * 14;
    if ( baseIdxSrcBuf >= pc.srcBuffFloatSize) {
        return;
    }
    int baseIdxWeightsBuf  = int(gl_GlobalInvocationID.x) * 8;
    vec4 weights = vec4(pc.weightsBuf.data[baseIdxWeightsBuf], pc.weightsBuf.data[baseIdxWeightsBuf + 1], pc.weightsBuf.data[baseIdxWeightsBuf + 2], pc.weightsBuf.data[baseIdxWeightsBuf + 3]);
    ivec4 joints = ivec4(pc.weightsBuf.data[baseIdxWeightsBuf + 4], pc.weightsBuf.data[baseIdxWeightsBuf + 5], pc.weightsBuf.data[baseIdxWeightsBuf + 6], pc.weightsBuf.data[baseIdxWeightsBuf + 7]);

    vec4 position = vec4(pc.srcBuf.data[baseIdxSrcBuf], pc.srcBuf.data[baseIdxSrcBuf + 1], pc.srcBuf.data[baseIdxSrcBuf + 2], 1);
    position =
    weights.x * pc.jointsBuf.data[joints.x] * position +
    weights.y * pc.jointsBuf.data[joints.y] * position +
    weights.z * pc.jointsBuf.data[joints.z] * position +
    weights.w * pc.jointsBuf.data[joints.w] * position;
    pc.dstBuf.data[baseIdxSrcBuf] = position.x / position.w;
    pc.dstBuf.data[baseIdxSrcBuf + 1] = position.y / position.w;
    pc.dstBuf.data[baseIdxSrcBuf + 2] = position.z / position.w;

    mat3 matJoint1 = mat3(transpose(inverse(pc.jointsBuf.data[joints.x])));
    mat3 matJoint2 = mat3(transpose(inverse(pc.jointsBuf.data[joints.y])));
    mat3 matJoint3 = mat3(transpose(inverse(pc.jointsBuf.data[joints.z])));
    baseIdxSrcBuf += 3;
    vec3 normal = vec3(pc.srcBuf .data[baseIdxSrcBuf], pc.srcBuf .data[baseIdxSrcBuf + 1], pc.srcBuf .data[baseIdxSrcBuf + 2]);
    normal =
    weights.x * matJoint1 * normal +
    weights.y * matJoint2 * normal +
    weights.z * matJoint3 * normal;
    normal = normalize(normal);
    pc.dstBuf.data[baseIdxSrcBuf] = normal.x;
    pc.dstBuf.data[baseIdxSrcBuf + 1] = normal.y;
    pc.dstBuf.data[baseIdxSrcBuf + 2] = normal.z;

    baseIdxSrcBuf += 3;
    vec3 tangent = vec3(pc.srcBuf .data[baseIdxSrcBuf], pc.srcBuf .data[baseIdxSrcBuf + 1], pc.srcBuf .data[baseIdxSrcBuf + 2]);
    tangent =
    weights.x * matJoint1 * tangent +
    weights.y * matJoint2 * tangent +
    weights.z * matJoint3 * tangent;
    tangent = normalize(tangent);
    pc.dstBuf.data[baseIdxSrcBuf] = tangent.x;
    pc.dstBuf.data[baseIdxSrcBuf + 1] = tangent.y;
    pc.dstBuf.data[baseIdxSrcBuf + 2] = tangent.z;

    baseIdxSrcBuf += 3;
    vec3 bitangent = vec3(pc.srcBuf .data[baseIdxSrcBuf], pc.srcBuf .data[baseIdxSrcBuf + 1], pc.srcBuf .data[baseIdxSrcBuf + 2]);
    bitangent =
    weights.x * matJoint1 * bitangent +
    weights.y * matJoint2 * bitangent +
    weights.z * matJoint3 * bitangent;
    bitangent = normalize(bitangent);
    pc.dstBuf.data[baseIdxSrcBuf] = bitangent.x;
    pc.dstBuf.data[baseIdxSrcBuf + 1] = bitangent.y;
    pc.dstBuf.data[baseIdxSrcBuf + 2] = bitangent.z;

    baseIdxSrcBuf += 3;
    vec2 textCoords = vec2(pc.srcBuf .data[baseIdxSrcBuf], pc.srcBuf .data[baseIdxSrcBuf + 1]);
    pc.dstBuf.data[baseIdxSrcBuf] = textCoords.x;
    pc.dstBuf.data[baseIdxSrcBuf + 1] = textCoords.y;
}
```

We will pass the addresses of the following buffers as push constants:
- The buffer that will contain binding pose vertices (`srcBuf`).
- The buffer that stores weights information (`weightsBuf`).
- The buffer that stores joint transformation matrices (`jointsBuf`).
- The buffer where we will dump the transformed vertices (`dstBuf`). This buffer is the one that will be used in the scene render stage.
- The size of the vertex buffer (`srcBuffFloatSize`).


You may have noticed that we first check if we are accessing indices above the size of the vertices buffer (`srcBuffFloatSize`). This is very important when using
BDA to prevent crashing the GPU by accessing illegal addresses. The rest of the code is quite similar, instead of relying in storage buffers, we access them through
their references. 

All these changes will simplify a lot the `AnimRender` class, since we do not need to create descriptor sets for all the potential buffer combinations per animated model and animated frames. We will need to create a buffer for push constants and properly set the push constants size when creating the pipeline:

```java
public class AnimRender {
    ...
    private static final int PUSH_CONSTANTS_SIZE = VkUtils.PTR_SIZE * 5;
    ...
    private final ByteBuffer pushConstBuff;
    ...
    public AnimRender(VkCtx vkCtx) {
        ...
        pushConstBuff = MemoryUtil.memAlloc(PUSH_CONSTANTS_SIZE);

        CompPipelineBuildInfo buildInfo = new CompPipelineBuildInfo(shaderModule, new DescSetLayout[]{
                stDescSetLayout, stDescSetLayout, stDescSetLayout, stDescSetLayout}, PUSH_CONSTANTS_SIZE);
        ...
    }
    ...
    public void cleanup(VkCtx vkCtx) {
        MemoryUtil.memFree(pushConstBuff);
        ...
    }
    ...
}
```

The `loadModels` method has been simplified a lot since we do not need to create descriptor sets for all the buffers, we just need to keep the code that assciates group
sizes to each mesh: 

```java
public class AnimRender {
    ...
    public void loadModels(ModelsCache modelsCache) {
        var models = modelsCache.getModelsMap().values();
        for (VulkanModel vulkanModel : models) {
            if (!vulkanModel.hasAnimations()) {
                continue;
            }
            for (VulkanMesh mesh : vulkanModel.getVulkanMeshList()) {
                int vertexSize = 14 * VkUtils.FLOAT_SIZE;
                int groupSize = (int) Math.ceil(((float) mesh.verticesBuffer().getRequestedSize() / vertexSize) /
                        LOCAL_SIZE_X);
                grpSizeMap.put(mesh.id(), groupSize);
            }
        }
    }
    ...
}
```

In the `render` method, we just need to get access to the addresses of the buffers instead of binding associated descriptor sets:

```java
public class AnimRender {
    ...
    public void render(EngCtx engCtx, VkCtx vkCtx, ModelsCache modelsCache, AnimationsCache animationsCache) {
        fence.fenceWait(vkCtx);
        fence.reset(vkCtx);

        try (var stack = MemoryStack.stackPush()) {
            recordingStart(vkCtx);

            VkUtils.memoryBarrier(cmdBuffer, VK_PIPELINE_STAGE_VERTEX_INPUT_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    0, VK_ACCESS_SHADER_WRITE_BIT, 0);

            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.getVkPipeline());

            Scene scene = engCtx.scene();

            List<Entity> entities = scene.getEntities();
            int numEntities = entities.size();
            for (int i = 0; i < numEntities; i++) {
                var entity = entities.get(i);
                String modelId = entity.getModelId();
                VulkanModel model = modelsCache.getModel(modelId);
                EntityAnimation entityAnimation = entity.getEntityAnimation();
                if (entityAnimation == null || !model.hasAnimations()) {
                    continue;
                }
                VulkanAnimation animation = model.getVulkanAnimationList().get(entityAnimation.getAnimationIdx());
                long jointsBuffAddress = animation.frameBufferList().get(entityAnimation.getCurrentFrame()).getAddress();
                List<VulkanMesh> vulkanMeshList = model.getVulkanMeshList();
                int numMeshes = vulkanMeshList.size();
                for (int j = 0; j < numMeshes; j++) {
                    var vulkanMesh = vulkanMeshList.get(j);

                    setPushConstants(cmdHandle,
                            vulkanMesh.verticesBuffer().getAddress(),
                            vulkanMesh.weightsBuffer().getAddress(),
                            jointsBuffAddress,
                            animationsCache.getBuffer(entity.getId(), vulkanMesh.id()).getAddress(),
                            vulkanMesh.verticesBuffer().getRequestedSize() / VkUtils.FLOAT_SIZE);

                    vkCmdDispatch(cmdHandle, grpSizeMap.get(vulkanMesh.id()), 1, 1);
                }
            }
            recordingStop();

            var cmds = VkCommandBufferSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .commandBuffer(cmdBuffer.getVkCommandBuffer());
            computeQueue.submit(cmds, null, null, fence);
        }
    }
    ...
}
```

Finally, we need to pass the buffer addresses as push constants:

```java
public class AnimRender {
    ...
    private void setPushConstants(VkCommandBuffer cmdHandle, long srcBufAddress, long weightsBufAddress,
                                  long jointsBufAddress, long dstAddress, long srcBuffFloatSize) {
        int offset = 0;
        pushConstBuff.putLong(offset, srcBufAddress);
        offset += VkUtils.PTR_SIZE;
        pushConstBuff.putLong(offset, weightsBufAddress);
        offset += VkUtils.PTR_SIZE;
        pushConstBuff.putLong(offset, jointsBufAddress);
        offset += VkUtils.PTR_SIZE;
        pushConstBuff.putLong(offset, dstAddress);
        offset += VkUtils.PTR_SIZE;
        pushConstBuff.putLong(offset, srcBuffFloatSize);
        vkCmdPushConstants(cmdHandle, pipeline.getVkPipelineLayout(), VK_SHADER_STAGE_COMPUTE_BIT, 0,
                pushConstBuff);
    }
}
```

## Final changes

We still have one important change to apply, all the buffers that are passed as references need to be created with the `VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT` flag.
This affects the `ModelsCache` class:

```java
public class ModelsCache {
    ...
    private static TransferBuffer createIndicesBuffers(VkCtx vkCtx, MeshData meshData, DataInputStream idxInput)
            throws IOException {
        ...
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);
        ...
    }

    private static TransferBuffer createJointMatricesBuffers(VkCtx vkCtx, AnimatedFrame frame) {
        ...
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                VMA_MEMORY_USAGE_AUTO, 0, 0);
        ...
    }

    private static TransferBuffer createVerticesBuffers(VkCtx vkCtx, MeshData meshData, DataInputStream vtxInput)
            throws IOException {
        ...
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
                        | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, VMA_MEMORY_USAGE_AUTO, 0, 0);
        ...
    }

    private static TransferBuffer createWeightsBuffers(VkCtx vkCtx, AnimMeshData animMeshData) {
        ...
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
                        | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, VMA_MEMORY_USAGE_AUTO, 0, 0);
        ...
    }
    ...
}
```

These changes also affect the `AnimationsCache` class:

```java
public class AnimationsCache {
    ...
    public void loadAnimations(VkCtx vkCtx, List<Entity> entities, ModelsCache modelsCache) {
        int numEntities = entities.size();
        for (int i = 0; i < numEntities; i++) {
            var entity = entities.get(i);
            VulkanModel model = modelsCache.getModel(entity.getModelId());
            if (!model.hasAnimations()) {
                continue;
            }
            Map<String, VkBuffer> bufferList = new HashMap<>();
            entitiesAnimBuffers.put(entity.getId(), bufferList);

            List<VulkanMesh> vulkanMeshList = model.getVulkanMeshList();
            int numMeshes = vulkanMeshList.size();
            for (int j = 0; j < numMeshes; j++) {
                var vulkanMesh = vulkanMeshList.get(j);
                VkBuffer animationBuffer = new VkBuffer(vkCtx, vulkanMesh.verticesBuffer().getRequestedSize(),
                        VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                        VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);
                bufferList.put(vulkanMesh.id(), animationBuffer);
            }
        }
    }
}
```

Finally, we just need to update the `Render` class to adapt to the signature changes of some of the `*Render` classes methods:

```java
public class Render {
    ...
    public void init(EngCtx engCtx, InitData initData) {
        ...
        animRender.loadModels(vkCtx, modelsCache, engCtx.scene().getEntities(), animationsCache);
    }
    ...
    public void render(EngCtx engCtx) {
        ...
        animRender.render(engCtx, vkCtx, modelsCache, animationsCache);
        ...
    }
    ...
}
```

And that's all! I hope you will appreciate how these changes simplify code. By getting rid of descriptor sets (or at least some of them) we already have an almost
bind-less render.

[Next chapter](../chapter-19/chapter-19.md)