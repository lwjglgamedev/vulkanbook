* Indirect drawing

Until this chapter, we render the models by binding their material uniforms, their textures, their vertices and indices buffers and submitting one draw command for each of the meshes it is composed. In this chapter we will start our way to a more efficient wat of rendering, we will begin the implementation of a bind-less render. This types of renders do not receive a bunch of draw commands, instead they relay on indirect drawing commands. Indirect draw commands are, in essence, draw commands that obtain the parameters required to perform the operation from a GPU buffer (instead of relaying on previous binding operations). This is a more efficient way of drawing because:

- We remove the need to perform several bind operations before drawing each mesh.
- We need just to record a single draw call.
- We can perform in-GPU operations, such as culling, that will operate over the buffer that stores the drawing parameters through compute shaders.

As you can see, the ultimate goal is to maximize the utilization of the CPU while removing potential bottlenecks that may occur at the CPU side and latencies due to CPU to GPU communications.

You can find the complete source code for this chapter [here](../../booksamples/chapter-16).

** Overview

The first thing to be done is to check if the device supports multi draw indirect. This is done in the `Device` class when setting up the required features:
```java
public class Device {
    ...
    public Device(Instance instance, PhysicalDevice physicalDevice) {
        ...
            if (!supportedFeatures.multiDrawIndirect()) {
                throw new RuntimeException("Multi draw Indirect not supported");
            }
            features.multiDrawIndirect(true);
        ...
    }
    ...
}
```

- GlobalBuffers

TBD

** TBD: Changes

- AnimationComputeActivity
- GeometryAttachments
- GeometryRenderActivity
- GuiRenderActivity
- ShadowRenderActivity
- VertexBufferStructure
- ComputePipeline
- DescriptorSetLayout
- InstancedVertexBufferStructure
- VulkanAnimENtity
- VulkanMOdel
- Render
- TextureCache
- IndexedLinkedHasMap
- Scene
- EngineProperties (TBD... WHEN) REQUIERED
- Main

TODO:

Move VulkanModel to graph pacakage in previous chapters.