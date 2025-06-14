# Chapter 19 - Indirect Drawing

Until this chapter, we have rendered the models by submitting one draw command for each of the meshes they are composed. In this chapter, we will start our way to a more
efficient way of rendering. This type of rendering does not receive a bunch of draw commands to draw the scene, instead they relay on indirect drawing commands.
Indirect draw commands are, in essence, draw commands stored in a buffer that obtain the parameters required to perform the operation from a set of global buffers. This is
a more efficient way of drawing because:

- We need just to record a single draw call.
- We can perform in-GPU operations, such as culling, that will operate over the buffer that stores the drawing parameters through compute shaders, reducing the load on the CPU side.

As you can see, the ultimate goal is to maximize the utilization of the GPU while removing potential bottlenecks that may occur at the CPU side and latencies due to CPU to GPU communications.

You can find the complete source code for this chapter [here](../../booksamples/chapter-19).

## Overview

As it has been introduced, we will use a buffer to store indirect drawing commands to render a scene with a single draw call. Basically, we will store in a buffer `VkDrawIndirectCommand` structures, which will contain information that will allow us to refer to the different buffers that will hold vertex information, the indices, materials and per instance data (such as model matrices, etc.). The `VkDrawIndirectCommand` is defined by the following fields:

- `vertexCount`: Number of vertices to be drawn.
- `instanceCount`: Number of instances to be drawn. If we have several entities that share the same model, we do not need to record an individual draw call for ach of the entity
and all the associated meshes. We can just ouse one command to draw a mesh for all the associated entities. We just nee dto set up the number of entities with this field.
- `firstVertex`: The index of the first vertex to be drawn.
- `firstInstance`: It can be used to setup a unique instance identifier to be used when drawing. We will see in the shaders how we can use it.

It is important to note, that we will note be recording calls to draw indirect commands. Instead, we will store them in a buffer and trigger the drawing by performing a single
call to the `vkCmdDrawIndirect` function. This requires us to restructure the code, and use additional buffers to properly refer to the data we will need.

In order to understand how we will use `VkDrawIndirectCommand`s, let's imagine we have the following structure (to simplify we will not use animated models): 

![Entities](rc19-entities.svg)

We will have three entities. The first two share the same model which has two meshes, while the third one has one model which is not shared by another entity and has also
two meshes. We will need to record the following `VkDrawIndirectCommand`s:

![Meshes](rc19-commands.svg)

With animated models it is not so simple to use instance render, since the entities, even if the share the same model, can be in a different animation state and therefore
will use specific vertex buffers that will hold the result of the animation.

We will create a buffer that will contain as many entries as entities and will hold the model matrices associated to each entity. In addition, in order to properly access the entity and per mesh data, we will create a specific buffer that will have as many entries as the number of entities multiplied vby the number of
meshes associated to them. Each entry in that buffer will contain:

- The index in the buffer that holds the model matrices associated to the entity.
- The material index (in previous chapters, since we recorded a draw call for each mesh and entity, we used push constants for that. Now, we cannot use tha approach since we
will submit a single draw call.)
- The address of the vertex buffer associated to the mesh.
- The address of the index buffer associated to the mesh.

The following figure shows the structures involved.

![Instances](rc19-instances.svg)

## Initial changes

We need to enable two features in the `Device` class:

- `multiDrawIndirect`: Since we will be using draw indirect capability.
- `drawIndirectFirstInstance`: When using draw indirect, we need to set up a proper instance ID for each of the involved draw call. Wew ill do it through the `firstInstance``
attribute of the `VkDrawIndirectCommand` structure. We need to enable this for this to work.

Changes in the `Device` class are like this:

```java
public class Device {
    ...
    public Device(PhysDevice physDevice) {
        ...
            features.depthClamp(depthClamp);
            features.multiDrawIndirect(true);
            features.shaderInt64(true);
            features.drawIndirectFirstInstance(true);
        ...
    }
    ...
}
```

We will need to organize our entities by the models to which they are associated, we will create a `Map` which will store, per model identifier, the list of entities
associated to that model. Therefore, we need to modify the `Scene` class like this:

```java
public class Scene {
    ...
    private final Map<String, List<Entity>> entities;
    ...
    public Scene(Window window) {
        entities = new HashMap<>();
        ...
    }

    public void addEntity(Entity entity) {
        var list = entities.computeIfAbsent(entity.getModelId(), k -> new ArrayList<>());
        list.add(entity);
    }
    ...
    public Map<String, List<Entity>> getEntities() {
        return entities;
    }
    ...
    public int getNumEntities() {
        return entities.values().stream().mapToInt(List::size).sum();
    }
    ...
    public void removeEntity(String entityId) {
        for (var list : entities.values()) {
            list.removeIf(entity1 -> entity1.getId().equals(entityId));
        }
    }
    ...
}
```

This change provokes that we need to update the `AnimationsCache` class since we need to transverse through the entities in a different way:

```java
public class AnimationsCache {
    ...
    public void loadAnimations(VkCtx vkCtx, Map<String, List<Entity>> entities, ModelsCache modelsCache) {
        for (var list : entities.values()) {
            int size = list.size();
            for (int i = 0; i < size; i++) {
                var entity = list.get(i);
                VulkanModel model = modelsCache.getModel(entity.getModelId());
                if (model == null) {
                    throw new RuntimeException("Model [" + entity.getModelId() + "] not found");
                }
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
                            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                            VMA_MEMORY_USAGE_AUTO, 0, 0);
                    bufferList.put(vulkanMesh.id(), animationBuffer);
                }
            }
        }
    }
}
```

We will create a class named `GlobalBuffers` that will holed some of the buffers described in the introduction and which starts like this:

```java
package org.vulkanb.eng.graph;

import org.lwjgl.system.*;
import org.lwjgl.vulkan.VkDrawIndirectCommand;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.nio.ByteBuffer;
import java.util.*;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

public class GlobalBuffers {
    public static final int IND_COMMAND_STRIDE = VkDrawIndirectCommand.SIZEOF;
    private static final int INSTANCE_DATA_SIZE = VkUtils.INT_SIZE * 2 + VkUtils.PTR_SIZE * 2;

    private final VkBuffer[] buffIndirectDrawCmds;
    private final VkBuffer[] buffInstanceData;
    private final VkBuffer[] buffModelMatrices;
    private final int[] drawCounts;

    public GlobalBuffers() {
        buffInstanceData = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
        buffModelMatrices = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
        buffIndirectDrawCmds = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
        drawCounts = new int[VkUtils.MAX_IN_FLIGHT];
    }

    public void cleanup(VkCtx vkCtx) {
        Arrays.asList(buffIndirectDrawCmds).forEach(b -> b.cleanup(vkCtx));
        Arrays.asList(buffModelMatrices).forEach(b -> b.cleanup(vkCtx));
        Arrays.asList(buffInstanceData).forEach(b -> b.cleanup(vkCtx));
    }
    ...
}
```

The buffers that we will use are:
- `buffIndirectDrawCmds`: This will hold the draw indirect commands.
- `buffInstanceData`: This will hold instance data (model matrix index, material index and the addresses for the vertex and index buffers).
- `buffModelMatrices`: This will hold model matrices for the entities.

You may have notices that we use array of buffers, we will have separate buffer instances per frame in flight. We then can update the contents of the buffers
associated to current frame while the ones associated to a previous frame, which may still be in use, can be safely accessed by the GPU. We will we upsizing
the buffers as long as we run if need more space. We will never shrink the buffers if the data to be used requires less space to avoid buffer recreation calls.
This is why we need to keep track of the number of indirect command buffers to be submitted, which is stored, per frame in flight, in the `drawCounts` array.
We simply cannot use the size of the buffers stored in `buffIndirectDrawCmds`, since their size may be higher than the actual number of commands to be drawn.

We will create a method named `createOrUpdateBuffers` which will be responsible of initializing the buffers in the array according to the number of commands,
entities or instances:

```java
public class GlobalBuffers {
    ...
    private void createOrUpdateBuffers(VkCtx vkCtx, int frame, int numIndCommands, int numInstanceData, int numEntities) {
        boolean create = false;
        if (buffIndirectDrawCmds[frame] != null && drawCounts[frame] < numIndCommands) {
            buffIndirectDrawCmds[frame].cleanup(vkCtx);
            create = true;
        }
        if (buffIndirectDrawCmds[frame] == null || create) {
            buffIndirectDrawCmds[frame] = new VkBuffer(vkCtx, (long) IND_COMMAND_STRIDE * numIndCommands,
                    VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        }
        drawCounts[frame] = numIndCommands;

        create = false;
        int bufferSize = numInstanceData * INSTANCE_DATA_SIZE;
        if (buffInstanceData[frame] != null && buffInstanceData[frame].getRequestedSize() < bufferSize) {
            buffInstanceData[frame].cleanup(vkCtx);
            create = true;
        }
        if (buffInstanceData[frame] == null || create) {
            buffInstanceData[frame] = new VkBuffer(vkCtx, bufferSize,
                    VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        }

        create = false;
        bufferSize = numEntities * VkUtils.MAT4X4_SIZE;
        if (buffModelMatrices[frame] != null && buffModelMatrices[frame].getRequestedSize() < bufferSize) {
            buffModelMatrices[frame].cleanup(vkCtx);
            create = true;
        }
        if (buffModelMatrices[frame] == null || create) {
            buffModelMatrices[frame] = new VkBuffer(vkCtx, bufferSize,
                    VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        }
    }
    ...
}
```

We will provide some *getters* to access the buffers and the draw count for the current frame:

```java
public class GlobalBuffers {
    ...
    public long getAddrBufInstanceData(int currentFrame) {
        return buffInstanceData[currentFrame].getAddress();
    }

    public long getAddrBufModeMatrices(int currentFrame) {
        return buffModelMatrices[currentFrame].getAddress();
    }

    public int getDrawCount(int currentFrame) {
        return drawCounts[currentFrame];
    }

    public VkBuffer getIndirectBuffer(int currentFrame) {
        return buffIndirectDrawCmds[currentFrame];
    }
    ...
}
```

Prior to render each frame, we need to populate the buffers, by calling the `update` method which is defined like this:

```java
public class GlobalBuffers {
    ...
    public void update(VkCtx vkCtx, Scene scene, ModelsCache modelsCache, AnimationsCache animationsCache,
                       MaterialsCache materialsCache, int frame) {
        try (var stack = MemoryStack.stackPush()) {
            Map<String, List<Entity>> entitiesMap = scene.getEntities();
            var indCommandList = new ArrayList<VkDrawIndirectCommand>();

            int baseEntityIdx = 0;
            int numInstanceData = 0;
            for (String key : entitiesMap.keySet()) {
                var entities = entitiesMap.get(key);
                int numEntities = entities.size();
                VulkanModel vulkanModel = modelsCache.getModel(key);
                List<VulkanMesh> vulkanMeshList = vulkanModel.getVulkanMeshList();
                int numMeshes = vulkanMeshList.size();
                if (vulkanModel.hasAnimations()) {
                    for (int i = 0; i < numMeshes; i++) {
                        for (int j = 0; j < numEntities; j++) {
                            var vulkanMesh = vulkanMeshList.get(i);

                            var indCommand = VkDrawIndirectCommand.calloc(stack)
                                    .vertexCount(vulkanMesh.numIndices())
                                    .instanceCount(1)
                                    .firstVertex(0)
                                    .firstInstance(baseEntityIdx);
                            indCommandList.add(indCommand);
                            baseEntityIdx++;
                            numInstanceData++;
                        }
                    }
                } else {
                    for (int j = 0; j < numMeshes; j++) {
                        var vulkanMesh = vulkanMeshList.get(j);
                        var indCommand = VkDrawIndirectCommand.calloc(stack)
                                .vertexCount(vulkanMesh.numIndices())
                                .instanceCount(numEntities)
                                .firstVertex(0)
                                .firstInstance(baseEntityIdx);
                        indCommandList.add(indCommand);
                        baseEntityIdx += numEntities;
                        numInstanceData += numEntities;
                    }
                }
            }
            int totalEntities = scene.getNumEntities();
            createOrUpdateBuffers(vkCtx, frame, indCommandList.size(), numInstanceData, totalEntities);

            updateCmdIndirectCommands(vkCtx, indCommandList, frame);
            updateInstanceData(vkCtx, entitiesMap, modelsCache, materialsCache, animationsCache, frame);
        }
    }
    ...
}
```

Let's dissect the method:
- We first iterate over the models and their associated entities.
- We get the meshes associated to the model.
- If the model is animated, we need to cerate an indirect command for each of the entities and meshes, no instanced render will be used. This is why we create
always the commands with `instanceCount` equal to `1`. We need to properly set the `firstInstance` vale. In this case, for  each mesh of the entity we just increase the value
with one. Later on, in the shaders we will use the built in variable `gl_InstanceIndex`, which will help us to navigate through the `buffInstanceData` data. In each mesh
that we will process for animated models for each entity the `gl_InstanceIndex` will be equal to the value set in the `firstInstance` field.
- For non animated models, we will just create one `VkDrawIndirectCommand` per mesh, setting the `instanceCount` to the number of entities associated to the model. In this case
we increment the `firstInstance` field by the number of entities. Imagine we are processing `Mesh 1` from the examples above. We will create just one `VkDrawIndirectCommand`
structure, setting the `instanceCount` field to `2` (since `Entity A` and `Entity B` are associated to the same model). When processing the vertices of `Mesh 1` for `Entity A`
, which we assume will be the first instance, the `gl_InstanceIndex` will have the value of the `firstInstance` field, that is `0`. When processing the  vertices of `Mesh 1` for
`Entity B`, which we assume will be the second instance, `gl_InstanceIndex` will have the value of the `firstInstance` field plus `1`, that is `1`. Now, for `Mesh 2`, when we
create the `VkDrawIndirectCommand` we will set up the `firstInstance` to `2`. When processing the vertices of `Mesh 12` for `Entity A` the `gl_InstanceIndex` will have the
value of the `firstInstance` field, that is `2`. When processing the  vertices of `Mesh 2` for `Entity B` `gl_InstanceIndex` will have the value of the `firstInstance` field plus `1`, that is `3`. You can see how this goes.
- After we have properly created the lis of `VkDrawIndirectCommand` structures, we call to the `createOrUpdateBuffers` to properly size the buffers and after that we call the
`updateCmdIndirectCommands` to dump the list of `VkDrawIndirectCommand` structures into the proper buffer and the `updateInstanceData` to populate the `buffInstanceData` and
the `buffModelMatrices` buffers.

The `updateCmdIndirectCommands` method is defined like this:

```java
public class GlobalBuffers {
    ...
    private void updateCmdIndirectCommands(VkCtx vkCtx, List<VkDrawIndirectCommand> indCommandList, int frame) {
        int numCommands = indCommandList.size();
        var buffer = buffIndirectDrawCmds[frame];
        ByteBuffer dataBuff = MemoryUtil.memByteBuffer(buffer.map(vkCtx), (int) buffer.getRequestedSize());
        VkDrawIndirectCommand.Buffer indCommandBuffer = new VkDrawIndirectCommand.Buffer(dataBuff);
        for (int i = 0; i < numCommands; i++) {
            indCommandBuffer.put(i, indCommandList.get(i));
        }
        buffer.unMap(vkCtx);
    }
    ...
}
```

We just dump in the `indCommandBuffer` the `VkDrawIndirectCommand` structures passed as a list for the current frame.

The `updateInstanceData` method is defined like this:

```java
public class GlobalBuffers {
    ...
    private void updateInstanceData(VkCtx vkCtx, Map<String, List<Entity>> entitiesMap, ModelsCache modelsCache,
                                    MaterialsCache materialsCache, AnimationsCache animationsCache, int frame) {
        var bufferInstances = buffInstanceData[frame];
        long mappedMemory = bufferInstances.map(vkCtx);
        ByteBuffer instanceData = MemoryUtil.memByteBuffer(mappedMemory, (int) bufferInstances.getRequestedSize());

        var bufferModels = buffModelMatrices[frame];
        mappedMemory = bufferModels.map(vkCtx);
        ByteBuffer matricesData = MemoryUtil.memByteBuffer(mappedMemory, (int) bufferModels.getRequestedSize());

        int baseEntities = 0;
        int offset = 0;
        for (String modelId : entitiesMap.keySet()) {
            List<Entity> entities = entitiesMap.get(modelId);
            int numEntities = entities.size();

            VulkanModel vulkanModel = modelsCache.getModel(modelId);
            boolean animatedModel = vulkanModel.hasAnimations();
            List<VulkanMesh> vulkanMeshList = vulkanModel.getVulkanMeshList();
            int numMeshes = vulkanMeshList.size();
            for (int i = 0; i < numMeshes; i++) {
                var mesh = vulkanMeshList.get(i);
                long vtxBufferAddress = mesh.verticesBuffer().getAddress();
                long idxBufferAddress = mesh.indicesBuffer().getAddress();
                for (int j = 0; j < numEntities; j++) {
                    Entity entity = entities.get(j);
                    int entityIdx = baseEntities + j;
                    entity.getModelMatrix().get(entityIdx * VkUtils.MAT4X4_SIZE, matricesData);

                    instanceData.putInt(offset, entityIdx);
                    offset += VkUtils.INT_SIZE;
                    var vulkanMesh = vulkanMeshList.get(i);
                    instanceData.putInt(offset, materialsCache.getPosition(vulkanMesh.materialdId()));
                    offset += VkUtils.INT_SIZE;
                    if (animatedModel) {
                        vtxBufferAddress = animationsCache.getBuffer(entity.getId(), vulkanMesh.id()).getAddress();
                    }
                    instanceData.putLong(offset, vtxBufferAddress);
                    offset += VkUtils.PTR_SIZE;
                    instanceData.putLong(offset, idxBufferAddress);
                    offset += VkUtils.PTR_SIZE;
                }
            }
            baseEntities += numEntities;
        }

        bufferInstances.unMap(vkCtx);
        bufferModels.unMap(vkCtx);
    }
}
```

We again iterate over the models and the entities. For each of the entities we just dump the model matrix in the `bufferModels`. For each of the meshes and entities
we also populate the structure stored in the `bufferInstances` buffer which are the index to access the model matrix, the material index and the addresses of the
buffers that hol vertices and indices data. In the case of animated models, we need to take care of selecting the proper vertices buffer that will hold the results
of applying the animation over the animation pose.

## Scene render changes

In order to properly understand why we are doing the changes in this way, we will sww now how do we use all of these structures when rendering the scene. We will see
first the changes in the scene vertex shader (`scn_vtx.glsl`):

```glsl
#version 450
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_buffer_reference2 : enable
#extension GL_EXT_scalar_block_layout : require

layout(location = 0) out vec4 outPos;
layout(location = 1) out vec3 outNormal;
layout(location = 2) out vec3 outTangent;
layout(location = 3) out vec3 outBitangent;
layout(location = 4) out vec2 outTextCoords;
layout(location = 5) flat out uint outMaterialIdx;

layout(set = 0, binding = 0) uniform ProjUniform {
    mat4 matrix;
} projUniform;
layout(set = 1, binding = 0) uniform ViewUniform {
    mat4 matrix;
} viewUniform;

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
    uint modelMatrixIdx;
    uint materialIdx;
    VertexBuffer vertexBuffer;
    IndexBuffer indexBuffer;
};

layout(std430, buffer_reference, buffer_reference_align=16) buffer InstancesDataBuffer {
    InstanceData[] instancesData;
};

layout(std430, buffer_reference, buffer_reference_align=8) buffer ModelMatricesDataBuffer {
    mat4[] modelMatrices;
};

layout(push_constant) uniform pc {
    InstancesDataBuffer instancesDataBuffer;
    ModelMatricesDataBuffer modelsMatricesDataBuffer;
} push_constants;

void main()
{
    uint entityId = gl_InstanceIndex;

    InstancesDataBuffer instancesDataBuffer = push_constants.instancesDataBuffer;
    InstanceData instanceData = instancesDataBuffer.instancesData[entityId];

    ModelMatricesDataBuffer modelMatricesDataBuffer = push_constants.modelsMatricesDataBuffer;
    mat4 modelMatrix = modelMatricesDataBuffer.modelMatrices[instanceData.modelMatrixIdx];

    VertexBuffer vertexBuffer = instanceData.vertexBuffer;
    IndexBuffer indexBuffer = instanceData.indexBuffer;

    uint index = indexBuffer.indices[gl_VertexIndex];
    Vertex vertex = vertexBuffer.vertices[index];

    vec3 inPos    = vertex.inPos;
    vec4 worldPos = modelMatrix * vec4(inPos, 1);
    mat3 mNormal  = transpose(inverse(mat3(modelMatrix)));

    outPos         = worldPos;
    outNormal      = mNormal * normalize(vertex.inNormal);
    outTangent     = mNormal * normalize(vertex.inTangent);
    outBitangent   = mNormal * normalize(vertex.inBitangent);
    outTextCoords  = vertex.inTextCoords;
    outMaterialIdx = instanceData.materialIdx;

    gl_Position   = projUniform.matrix * viewUniform.matrix * worldPos;
}
```

Firs we need to pass the material index to the fragment shader as an outout variable (`outMaterialIdx`), since it will not be available through push constants. We use
the `flat` modifier since we do not want to perform any kind of interpolation. Push constants now just define addresses for the instances buffer (the one we defined
in the `GlobalBuffers` class as `buffInstanceData`) and the buffer tha hold models matrices. The `InstancesDataBuffer` just defines the data explained above, the
model matrix index, the material index and the addresses of the vertices and indices buffers. In order to access the proper element in that buffer, we just use the
`gl_InstanceIndex` built in variable as explained previously. The rest of the code is quite similar, once we get the proper references, we process the vertices in the same
way. We need only to pass the material index to be used in the fragment shader (`scn_frg.glsl`), which needs only to be changed a little bit to retrieve that value from 
input parameters instead of through a push constant:

```glsl
#version 450

// Keep in sync manually with Java code
const int MAX_TEXTURES = 100;

layout(location = 0) in vec4 inPos;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in vec3 inTangent;
layout(location = 3) in vec3 inBitangent;
layout(location = 4) in vec2 inTextCoords;
layout(location = 5) flat in uint inMaterialIdx;

layout(location = 0) out vec4 outPos;
layout(location = 1) out vec4 outAlbedo;
layout(location = 2) out vec4 outNormal;
layout(location = 3) out vec4 outPBR;

struct Material {
    vec4 diffuseColor;
    uint hasTexture;
    uint textureIdx;
    uint hasNormalMap;
    uint normalMapIdx;
    uint hasRoughMap;
    uint roughMapIdx;
    float roughnessFactor;
    float metallicFactor;
};
layout(set = 2, binding = 0) readonly buffer MaterialUniform {
    Material materials[];
} matUniform;
layout(set = 3, binding = 0) uniform sampler2D textSampler[MAX_TEXTURES];

vec3 calcNormal(Material material, vec3 normal, vec2 textCoords, mat3 TBN)
{
    vec3 newNormal = normal;
    if (material.hasNormalMap > 0)
    {
        newNormal = texture(textSampler[material.normalMapIdx], textCoords).rgb;
        newNormal = normalize(newNormal * 2.0 - 1.0);
        newNormal = normalize(TBN * newNormal);
    }
    return newNormal;
}

void main()
{
    outPos = inPos;

    Material material = matUniform.materials[inMaterialIdx];
    if (material.hasTexture == 1) {
        outAlbedo = texture(textSampler[material.textureIdx], inTextCoords);
    } else {
        outAlbedo = material.diffuseColor;
    }

    // Hack to avoid transparent PBR artifacts
    if (outAlbedo.a < 0.5) {
        discard;
    }

    mat3 TBN = mat3(inTangent, inBitangent, inNormal);
    vec3 newNormal = calcNormal(material, inNormal, inTextCoords, TBN);
    outNormal = vec4(newNormal, 1.0);

    float ao = 0.5f;
    float roughnessFactor = 0.0f;
    float metallicFactor = 0.0f;
    if (material.hasRoughMap > 0) {
        vec4 metRoughValue = texture(textSampler[material.roughMapIdx], inTextCoords);
        roughnessFactor = metRoughValue.g;
        metallicFactor = metRoughValue.b;
    } else {
        roughnessFactor = material.roughnessFactor;
        metallicFactor = material.metallicFactor;
    }

    outPBR = vec4(ao, roughnessFactor, metallicFactor, 1.0f);
}
```

Now it is turn to review the changes in the `ScnRender` class:

```java
public class ScnRender {
    ...
    private static final int PUSH_CONSTANTS_SIZE = VkUtils.PTR_SIZE * 2;
    ...
    public void render(EngCtx engCtx, VkCtx vkCtx, CmdBuffer cmdBuffer, GlobalBuffers globalBuffers, int currentFrame) {
        ...
            VkUtils.copyMatrixToBuffer(vkCtx, buffViewMatrices[currentFrame], engCtx.scene().getCamera().getViewMatrix(), 0);
            DescAllocator descAllocator = vkCtx.getDescAllocator();
            LongBuffer descriptorSets = stack.mallocLong(4)
                    .put(0, descAllocator.getDescSet(DESC_ID_PRJ).getVkDescriptorSet())
                    .put(1, descAllocator.getDescSet(DESC_ID_VIEW, currentFrame).getVkDescriptorSet())
                    .put(2, descAllocator.getDescSet(DESC_ID_MAT).getVkDescriptorSet())
                    .put(3, descAllocator.getDescSet(DESC_ID_TEXT).getVkDescriptorSet());
            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipelineLayout(),
                    0, descriptorSets, null);

            setPushConstants(cmdHandle, globalBuffers, currentFrame);

            vkCmdDrawIndirect(cmdHandle, globalBuffers.getIndirectBuffer(currentFrame).getBuffer(), 0,
                    globalBuffers.getDrawCount(currentFrame), GlobalBuffers.IND_COMMAND_STRIDE);

            vkCmdEndRendering(cmdHandle);
        ...
    }
    ...
    private void setPushConstants(VkCommandBuffer cmdHandle, GlobalBuffers globalBuffers, int currentFrame) {
        int offset = 0;
        pushConstBuff.putLong(offset, globalBuffers.getAddrBufInstanceData(currentFrame));
        offset += VkUtils.PTR_SIZE;
        pushConstBuff.putLong(offset, globalBuffers.getAddrBufModeMatrices(currentFrame));
        vkCmdPushConstants(cmdHandle, pipeline.getVkPipelineLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstBuff);
    }
}
```

As you can see the changes are quite minimal. We just need to update the size of the push constants and in the `render` method we just call the `vkCmdDrawIndirect`
function, with the buffer we previously filled up in the `updateCmdIndirectCommands` method form the `GlobalBuffers` class. The `renderEntities` method is now gone.
The `setPushConstants` method needs to be simplified to pass just the two addresses we will need. 

## Shadow render changes

Changes in the shadow render stage are quite similar to the ones applied in the scene render one. We need to update the vertex shader (`shadow_vtx.glsl`) to use the same
structures:

```glsl
#version 450
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_buffer_reference2 : enable
#extension GL_EXT_scalar_block_layout : require

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
    uint modelMatrixIdx;
    uint materialIdx;
    VertexBuffer vertexBuffer;
    IndexBuffer indexBuffer;
};

layout(std430, buffer_reference, buffer_reference_align=16) buffer InstancesDataBuffer {
    InstanceData[] instancesData;
};

layout(std430, buffer_reference, buffer_reference_align=8) buffer ModelMatricesDataBuffer {
    mat4[] modelMatrices;
};

layout(push_constant) uniform pc {
    InstancesDataBuffer instancesDataBuffer;
    ModelMatricesDataBuffer modelsMatricesDataBuffer;
} push_constants;

layout (location = 0) out vec2 outTextCoords;
layout (location = 1) out flat uint outMaterialIdx;

void main()
{
    uint entityId = gl_InstanceIndex;

    InstancesDataBuffer instancesDataBuffer = push_constants.instancesDataBuffer;
    InstanceData instanceData = instancesDataBuffer.instancesData[entityId];

    VertexBuffer vertexBuffer = instanceData.vertexBuffer;
    IndexBuffer indexBuffer = instanceData.indexBuffer;

    ModelMatricesDataBuffer modelMatricesDataBuffer = push_constants.modelsMatricesDataBuffer;
    mat4 modelMatrix = modelMatricesDataBuffer.modelMatrices[instanceData.modelMatrixIdx];

    uint index = indexBuffer.indices[gl_VertexIndex];
    Vertex vertex = vertexBuffer.vertices[index];

    vec3 inPos     = vertex.inPos;
    vec4 worldPos  = modelMatrix * vec4(inPos, 1);
    outTextCoords  = vertex.inTextCoords;
    outMaterialIdx = instanceData.materialIdx;

    gl_Position    = worldPos;
}
```

Changes in the `ShadowRender` class are similar to the ones in `ScnRender` class:

```java
public class ShadowRender {
    ...
    private static final int PUSH_CONSTANTS_SIZE = VkUtils.PTR_SIZE * 2;
    ...
    public void render(EngCtx engCtx, VkCtx vkCtx, CmdBuffer cmdBuffer, GlobalBuffers globalBuffers, int currentFrame) {
        ...
            LongBuffer descriptorSets = stack.mallocLong(3)
                    .put(0, descAllocator.getDescSet(DESC_ID_PRJ, currentFrame).getVkDescriptorSet())
                    .put(1, descAllocator.getDescSet(DESC_ID_TEXT).getVkDescriptorSet())
                    .put(2, descAllocator.getDescSet(DESC_ID_MAT).getVkDescriptorSet());
            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipelineLayout(),
                    0, descriptorSets, null);

            setPushConstants(cmdHandle, globalBuffers, currentFrame);

            vkCmdDrawIndirect(cmdHandle, globalBuffers.getIndirectBuffer(currentFrame).getBuffer(), 0,
                    globalBuffers.getDrawCount(currentFrame), GlobalBuffers.IND_COMMAND_STRIDE);
        ...
    }

    private void setPushConstants(VkCommandBuffer cmdHandle, GlobalBuffers globalBuffers, int currentFrame) {
        int offset = 0;
        pushConstBuff.putLong(offset, globalBuffers.getAddrBufInstanceData(currentFrame));
        offset += VkUtils.PTR_SIZE;
        pushConstBuff.putLong(offset, globalBuffers.getAddrBufModeMatrices(currentFrame));
        vkCmdPushConstants(cmdHandle, pipeline.getVkPipelineLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstBuff);
    }
    ...
}
```

## Final changes

Animation render (`AnimRender`) needs just to be changes since the way we traverse thorugh the entities of animated models as now changes, that is all. The concepts
are still the same:

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

            Map<String, List<Entity>> entitiesMap = scene.getEntities();
            for (var list : entitiesMap.values()) {
                int size = list.size();
                for (int i = 0; i < size; i++) {
                    var entity = list.get(i);
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

Finally, the `Render` class needs also to be updated to adapt to the changes in the signatures on some of the classes it uses and the need to instantiate a 
`GlobalBuffers` instance:

```java
public class Render {
    ...
    private final GlobalBuffers globalBuffers;
    ...
    public Render(EngCtx engCtx) {
        ...
        globalBuffers = new GlobalBuffers();
    }

    public void cleanup() {
        ...
        globalBuffers.cleanup(vkCtx);
        ...
    }
    ...
    public void render(EngCtx engCtx) {
        ...
        globalBuffers.update(vkCtx, engCtx.scene(), modelsCache, animationsCache, materialsCache, currentFrame);

        scnRender.render(engCtx, vkCtx, cmdBuffer, globalBuffers, currentFrame);
        shadowRender.render(engCtx, vkCtx, cmdBuffer, globalBuffers, currentFrame);
        ...
    }
    ...
}
```

With all these changes we have BDA plus indirect drawing. If you want to even create a more performant code, you could use a single buffer to hold vertices buffers
for all the meshes of all the models. you will just need to update the offset fields when filling up the indirect command buffer. Depending if you use indexed render or not
that offset would refer to the indices buffer or the vertices one (taking care of adapting indices to the fact that al the vertices are now in a single structure).