# Cascade Shadows

In this chapter, we will add shadows to the scene applying Cascaded Shadow Maps (CSM). This chapter applies the techniques shown by Sascha Willems in his Vulkan examples. Specifically, it uses part of the source code for the examples related to [cascaded shadow mapping](https://github.com/SaschaWillems/Vulkan/blob/master/examples/shadowmappingcascade) and [deferred shadow mapping(https://github.com/SaschaWillems/Vulkan/blob/master/examples/deferredshadows). I cannot stress enough how good are the examples provided by Sascha Willems, you should carefully have a look at them.

You can find the complete source code for this chapter [here](../../booksamples/chapter-13).

## Cascade shadow mapping overview

In order to render shadows, we just need to render the scene from the light point of view creating a depth map. Later on, when rendering the scene, we transform the coordinates of the fragment being rendered to the light view space and check its depth. If its depth is lower than the depth stored in the depth map for those coordinates, it will mean that the fragment is not in shadows. In our case, we will be calculating shadows for a single directional light, so when rendering the depth map we will be using an orthographic projection (you can think about directional light as a source which casts parallel rays from the infinity. Those rays do not converge at a focal point).

<img src="light-projection.svg" title="" alt="Light projection" data-align="center">

The problem with shadow depth maps is their resolution, we need to cover a wide area, and in order to get high quality visuals we would need huge images to store that information. One possible solution for that are cascade shadow maps. It is based on the fact that, shadows of objects that are closer to the camera need to have a higher quality than shadows for distant objects. The approach that Cascaded Shadow Maps (CSMs) use is to divide the view frustum into several splits. Splits closer to the camera cover a smaller amount of space whilst distant regions cover much wider regions. CSMs use one depth map per split. For each of these splits, the depth map is rendered, adjusting the light view and projection matrices to cover each split.

## Rendering the depth map

We will start by creating a new package, `org.vulkanb.eng.graph.shadows`, that will hold all the code related to calculate and render the shadow maps. The first class in this package will be responsible of calculating the matrices required to render the shadow maps from light perspective. The class is named `CascadeShadow` and will store the projection view matrix (from light perspective) for a specific cascade shadow split (`projViewMatrix` attribute) and the far plane distance for its ortho-projection matrix (`splitDistance` attribute):

```java
package org.vulkanb.eng.graph.shadows;
...
public class CascadeShadow {

    private Matrix4f projViewMatrix;
    private float splitDistance;

    public CascadeShadow() {
        projViewMatrix = new Matrix4f();
    }
    ...
    public Matrix4f getProjViewMatrix() {
        return projViewMatrix;
    }

    public float getSplitDistance() {
        return splitDistance;
    }
    ...
}
```

The `CascadeShadow` class defines a static method to initialize a list of cascade shadows instances with the proper values named `updateCascadeShadows`. This method starts like this:

```java
public class CascadeShadow {
    ...
    public static void updateCascadeShadows(List<CascadeShadow> cascadeShadows, Scene scene) {
        Matrix4f viewMatrix = scene.getCamera().getViewMatrix();
        Matrix4f projMatrix = scene.getProjection().getProjectionMatrix();
        Vector4f lightPos = scene.getDirectionalLight().getPosition();

        float cascadeSplitLambda = 0.95f;

        float[] cascadeSplits = new float[GraphConstants.SHADOW_MAP_CASCADE_COUNT];

        float nearClip = projMatrix.perspectiveNear();
        float farClip = projMatrix.perspectiveFar();
        float clipRange = farClip - nearClip;

        float minZ = nearClip;
        float maxZ = nearClip + clipRange;

        float range = maxZ - minZ;
        float ratio = maxZ / minZ;
        ...
    }
    ...
}
```

We start by retrieving the matrices that we will need to calculate the splits data, the view and projection matrices, the light position and the near and far clips of the perspective projection we are using to render the scene. With that information we can calculate the split distances for each of the shadow cascades:

```java
public class CascadeShadow {
    ...
    public static void updateCascadeShadows(List<CascadeShadow> cascadeShadows, Scene scene) {
        ...
        // Calculate split depths based on view camera frustum
        // Based on method presented in https://developer.nvidia.com/gpugems/GPUGems3/gpugems3_ch10.html
        for (int i = 0; i < GraphConstants.SHADOW_MAP_CASCADE_COUNT; i++) {
            float p = (i + 1) / (float) (GraphConstants.SHADOW_MAP_CASCADE_COUNT);
            float log = (float) (minZ * java.lang.Math.pow(ratio, p));
            float uniform = minZ + range * p;
            float d = cascadeSplitLambda * (log - uniform) + uniform;
            cascadeSplits[i] = (d - nearClip) / clipRange;
        }
        ...
    }
    ...
}
```

The algorithm used to calculate the split positions, uses a logarithm schema to better distribute the distances. We could just use other different approaches, such as splitting the cascades evenly, or according to a pre-set proportion,. The advantage of the logarithm schema is that it uses less space for near view splits, achieving a higher resolution for the elements closer to the camera. You can check the [NVIDIA article](https://developer.nvidia.com/gpugems/GPUGems3/gpugems3_ch10.html) for the math details. The `cascadeSplits` array will have a set of values in the range [0, 1] which we will use later on to perform the required calculations to get the split distances and the projection matrices for each cascade.

Now we define a loop to calculate all the data for the cascade splits. In that loop, we first create the frustum corners in NDC (Normalized Device Coordinates) space. After that, we project those coordinates into world space by using the inverse of the view and perspective matrices. Since we are using directional lights, we will use ortographic projection matrices for rendering the shadow maps, this is the reason why we set, as the NDC coordinates, just the limits of the cube that contains the visible volume (distant objects will not be rendered smaller, as in the perspective projection).

```java
public class CascadeShadow {
    ...
    public static void updateCascadeShadows(List<CascadeShadow> cascadeShadows, Scene scene) {
        ...
        // Calculate orthographic projection matrix for each cascade
        float lastSplitDist = 0.0f;
        for (int i = 0; i < GraphConstants.SHADOW_MAP_CASCADE_COUNT; i++) {
            float splitDist = cascadeSplits[i];

            Vector3f[] frustumCorners = new Vector3f[]{
                    new Vector3f(-1.0f, 1.0f, 0.0f),
                    new Vector3f(1.0f, 1.0f, 0.0f),
                    new Vector3f(1.0f, -1.0f, 0.0f),
                    new Vector3f(-1.0f, -1.0f, 0.0f),
                    new Vector3f(-1.0f, 1.0f, 1.0f),
                    new Vector3f(1.0f, 1.0f, 1.0f),
                    new Vector3f(1.0f, -1.0f, 1.0f),
                    new Vector3f(-1.0f, -1.0f, 1.0f),
            };

            // Project frustum corners into world space
            Matrix4f invCam = (new Matrix4f(projMatrix).mul(viewMatrix)).invert();
            for (int j = 0; j < 8; j++) {
                Vector4f invCorner = new Vector4f(frustumCorners[j], 1.0f).mul(invCam);
                frustumCorners[j] = new Vector3f(invCorner.x / invCorner.w, invCorner.y / invCorner.w, invCorner.z / invCorner.w);
            }
            ...
        }
        ...
    }
    ...
}
```

At this point, `frustumCorners` variable has the coordinates of a cube which contains the visible space, but we need the world coordinates for this specific cascade split. Therefore, the next step is to put the cascade distances calculated at the beginning of them method into work. We adjust the coordinates of near and far planes for this specific split according to the pre-calculated distances:

```java
public class CascadeShadow {
    ...
    public static void updateCascadeShadows(List<CascadeShadow> cascadeShadows, Scene scene) {
        ...
        for (int i = 0; i < GraphConstants.SHADOW_MAP_CASCADE_COUNT; i++) {
            ...
            for (int j = 0; j < 4; j++) {
                Vector3f dist = new Vector3f(frustumCorners[j + 4]).sub(frustumCorners[j]);
                frustumCorners[j + 4] = new Vector3f(frustumCorners[j]).add(new Vector3f(dist).mul(splitDist));
                frustumCorners[j] = new Vector3f(frustumCorners[j]).add(new Vector3f(dist).mul(lastSplitDist));
            }
            ...
        }
        ...
    }
    ...
}
```

After that, we calculate the coordinates of the center of that split (still working in world coordinates), and the radius of that split:

```java
public class CascadeShadow {
    ...
    public static void updateCascadeShadows(List<CascadeShadow> cascadeShadows, Scene scene) {
        ...
        for (int i = 0; i < GraphConstants.SHADOW_MAP_CASCADE_COUNT; i++) {
            ...
            // Get frustum center
            Vector3f frustumCenter = new Vector3f(0.0f);
            for (int j = 0; j < 8; j++) {
                frustumCenter.add(frustumCorners[j]);
            }
            frustumCenter.div(8.0f);

            float radius = 0.0f;
            for (int j = 0; j < 8; j++) {
                float distance = (new Vector3f(frustumCorners[j]).sub(frustumCenter)).length();
                radius = java.lang.Math.max(radius, distance);
            }
            radius = (float) java.lang.Math.ceil(radius * 16.0f) / 16.0f;
            ...
        }
        ...
    }
    ...
}
```

With that information, we can now calculate the view matrix, from the light point of view and the orthographic projection matrix as well as the split distance (in camera view coordinates):

```java
public class CascadeShadow {
    ...
    public static void updateCascadeShadows(List<CascadeShadow> cascadeShadows, Scene scene) {
        ...
        for (int i = 0; i < GraphConstants.SHADOW_MAP_CASCADE_COUNT; i++) {
            ...
            Vector3f maxExtents = new Vector3f(radius);
            Vector3f minExtents = new Vector3f(maxExtents).mul(-1);

            Vector3f lightDir = (new Vector3f(lightPos.x, lightPos.y, lightPos.z).mul(-1)).normalize();
            Vector3f eye = new Vector3f(frustumCenter).sub(new Vector3f(lightDir).mul(-minExtents.z));
            Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);
            Matrix4f lightViewMatrix = new Matrix4f().lookAt(eye, frustumCenter, up);
            Matrix4f lightOrthoMatrix = new Matrix4f().ortho
                    (minExtents.x, maxExtents.x, minExtents.y, maxExtents.y, 0.0f, maxExtents.z - minExtents.z, true);

            // Store split distance and matrix in cascade
            CascadeShadow cascadeShadow = cascadeShadows.get(i);
            cascadeShadow.splitDistance = (nearClip + splitDist * clipRange) * -1.0f;
            cascadeShadow.projViewMatrix = lightOrthoMatrix.mul(lightViewMatrix);

            lastSplitDist = cascadeSplits[i];
        }
        ...
    }
    ...
}
```

As it can be deducted, we have created a new constant in the `GraphConstants` class to define the number of splits:

```java
public final class GraphConstants {
    ...
    public static final int SHADOW_MAP_CASCADE_COUNT = 3;
    ...
}
```

We have now completed the code that calculates the matrices required to render the shadow maps. Therefore, we can start coding the classes required to perform that rendering. In this case, we will be rendering to a different image (a depth image), and will require specific shaders. Therefore, we will first need a new class to encapsulate the creation of the render pass named `ShadowsRenderPass`. The class is quite similar to the render pass creation class used to render the scene geometry information:

```java
package org.vulkanb.eng.graph.shadows;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class ShadowsRenderPass {

    private static final int MAX_SAMPLES = 1;
    
    private final Device device;
    private final long vkRenderPass;

    public ShadowsRenderPass(Device device, Attachment depthAttachment) {
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAttachmentDescription.Buffer attachmentsDesc = VkAttachmentDescription.calloc(1, stack);
            attachmentsDesc.get(0)
                    .format(depthAttachment.getImage().getFormat())
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .samples(MAX_SAMPLES)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL);

            VkAttachmentReference depthReference = VkAttachmentReference.calloc(stack)
                    .attachment(0)
                    .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            // Render subpass
            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .pDepthStencilAttachment(depthReference);

            // Subpass dependencies
            VkSubpassDependency.Buffer subpassDependencies = VkSubpassDependency.calloc(2, stack);
            subpassDependencies.get(0)
                    .srcSubpass(VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .srcAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);

            subpassDependencies.get(1)
                    .srcSubpass(0)
                    .dstSubpass(VK_SUBPASS_EXTERNAL)
                    .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                    .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);

            // Render pass
            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(attachmentsDesc)
                    .pSubpasses(subpass)
                    .pDependencies(subpassDependencies);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateRenderPass(device.getVkDevice(), renderPassInfo, null, lp),
                    "Failed to create render pass");
            vkRenderPass = lp.get(0);
        }
    }

    public void cleanup() {
        vkDestroyRenderPass(device.getVkDevice(), vkRenderPass, null);
    }

    public long getVkRenderPass() {
        return vkRenderPass;
    }
}
```

The next step is to create a new class named `ShadowsFrameBuffer` that, as in the geometry and light pass, will encapsulate the attachment, render pass and frame buffer creation. Its constructor is defined like this:

```java
public class ShadowsFrameBuffer {
    ...
    public ShadowsFrameBuffer(Device device) {
        Logger.debug("Creating ShadowsFrameBuffer");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
            EngineProperties engineProperties = EngineProperties.getInstance();
            int shadowMapSize = engineProperties.getShadowMapSize();
            Image.ImageData imageData = new Image.ImageData().width(shadowMapSize).height(shadowMapSize).
                    usage(usage | VK_IMAGE_USAGE_SAMPLED_BIT).
                    format(VK_FORMAT_D32_SFLOAT).arrayLayers(GraphConstants.SHADOW_MAP_CASCADE_COUNT);
            Image depthImage = new Image(device, imageData);

            int aspectMask = Attachment.calcAspectMask(usage);

            ImageView.ImageViewData imageViewData = new ImageView.ImageViewData().format(depthImage.getFormat()).
                    aspectMask(aspectMask).viewType(VK_IMAGE_VIEW_TYPE_2D_ARRAY).
                    layerCount( GraphConstants.SHADOW_MAP_CASCADE_COUNT);
            ImageView depthImageView = new ImageView(device, depthImage.getVkImage(), imageViewData);
            depthAttachment = new Attachment(depthImage, depthImageView, true);

            shadowsRenderPass = new ShadowsRenderPass(device, depthAttachment);

            LongBuffer attachmentsBuff = stack.mallocLong(1);
            attachmentsBuff.put(0, depthAttachment.getImageView().getVkImageView());
            frameBuffer = new FrameBuffer(device, shadowMapSize, shadowMapSize, attachmentsBuff,
                    shadowsRenderPass.getVkRenderPass(), GraphConstants.SHADOW_MAP_CASCADE_COUNT);
        }
    }
    ...
}
```

In this specific case, we are handling the creation of the image that will hold the depth values for the cascade shadow maps manually (instead of delegating this to the `Attachment` class). We will use a layered image, in which each layer will hold the depth values for each of the cascade splits. We will need to take this into consideration when creating the image. This multi-layered image approach needs also be considered in the image view associated to the image.

Due to the previous changes, the `Attachment` class also needs to be modified to allow external classes to directly pass references to `Image` and `ImageViews` instances instead of creating them in the constructor. The code to calculate the aspect mask of and image view based on its associated image has also been extracted to a new method named `calcAspectMask`.

```java
public class Attachment {
    ...
    public Attachment(Image image, ImageView imageView, boolean depthAttachment) {
        this.image = image;
        this.imageView = imageView;
        this.depthAttachment = depthAttachment;
    }

    public Attachment(Device device, int width, int height, int format, int usage) {
        Image.ImageData imageData = new Image.ImageData().width(width).height(height).
                usage(usage | VK_IMAGE_USAGE_SAMPLED_BIT).
                format(format);
        image = new Image(device, imageData);

        int aspectMask = calcAspectMask(usage);
        depthAttachment = aspectMask == VK_IMAGE_ASPECT_DEPTH_BIT;

        ImageView.ImageViewData imageViewData = new ImageView.ImageViewData().format(image.getFormat()).aspectMask(aspectMask);
        imageView = new ImageView(device, image.getVkImage(), imageViewData);
    }

    public static int calcAspectMask(int usage) {
        int aspectMask = 0;
        if ((usage & VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) > 0) {
            aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        }
        if ((usage & VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT) > 0) {
            aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT;
        }
        return aspectMask;
    }
    ...
}
```

Going back to the `ShadowsFrameBuffer` class, the rest of the code is similar to the one used in the geometry or lighting phases, with the exception of the frame buffer creation. `FrameBuffer` class needs also to be aware that we are using multi-layered attachments, therefore, as in the `Image` class, it needs also to be modified to support multi-layered images.

```java
public class FrameBuffer {
    ...
    public FrameBuffer(Device device, int width, int height, LongBuffer pAttachments, long renderPass, int layers) {
        ...
            VkFramebufferCreateInfo fci = VkFramebufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                    .pAttachments(pAttachments)
                    .width(width)
                    .height(height)
                    .layers(layers)
                    .renderPass(renderPass);
        ...
    }
    ...
}
```

Finally, the `ShadowsFrameBuffer` class defines a `cleanup` and `getter`methods:

```java
public class ShadowsFrameBuffer {
    ...
    public void cleanup() {
        Logger.debug("Destroying ShadowsFrameBuffer");
        shadowsRenderPass.cleanup();
        depthAttachment.cleanup();
        frameBuffer.cleanup();
    }

    public Attachment getDepthAttachment() {
        return depthAttachment;
    }

    public FrameBuffer getFrameBuffer() {
        return frameBuffer;
    }

    public ShadowsRenderPass getRenderPass() {
        return shadowsRenderPass;
    }
}
```

The next step is to create a new class that will control the rendering of the shadow maps. The class will be named `ShadowRenderActivity` and will render the scene from the light point of view for each shadow split. That information will be stored as a depth map, which in our case, will be a multi-layered image. One approach to achieve this is to render the scene, from the light point of view for each of the cascades individually. We would be rendering the scene as many times as cascade splits we have, storing the depth information for each split in a layer. We can do this much better, we could achieve the same results just submitting the drawing commands for the scene elements once, by using a geometry shader. Geometry shaders are executed between vertex and fragment shaders, allowing us to transform the primitives. In this specific case, we will use them to generate new primitives, one for each of the cascade splits taking as an input the original primitives which are generated in the vertex shader while rendering the scene. That is, taking a single triangle we will be generating three triangles, one per cascade split. We will see the details when examining the shaders, however, keep in mind that in this case we will be using a set of vertex-geometry-fragment shaders, instead of the usual vertex-fragment shaders pair that we have been employing on previous chapters.

The `ShadowRenderActivity` class starts like this:

```java
public class ShadowRenderActivity {

    private static final String SHADOW_FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/shadow_fragment.glsl";
    private static final String SHADOW_FRAGMENT_SHADER_FILE_SPV = SHADOW_FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final String SHADOW_GEOMETRY_SHADER_FILE_GLSL = "resources/shaders/shadow_geometry.glsl";
    private static final String SHADOW_GEOMETRY_SHADER_FILE_SPV = SHADOW_GEOMETRY_SHADER_FILE_GLSL + ".spv";
    private static final String SHADOW_VERTEX_SHADER_FILE_GLSL = "resources/shaders/shadow_vertex.glsl";
    private static final String SHADOW_VERTEX_SHADER_FILE_SPV = SHADOW_VERTEX_SHADER_FILE_GLSL + ".spv";

    private List<CascadeShadow> cascadeShadows;
    private DescriptorPool descriptorPool;
    private DescriptorSetLayout[] descriptorSetLayouts;
    private Map<String, TextureDescriptorSet> descriptorSetMap;
    private boolean firstRun;
    private Device device;
    private Pipeline pipeLine;
    private DescriptorSet.UniformDescriptorSet[] projMatrixDescriptorSet;
    private Scene scene;
    private ShaderProgram shaderProgram;
    private ShadowsFrameBuffer shadowsFrameBuffer;
    private VulkanBuffer[] shadowsUniforms;
    private SwapChain swapChain;
    private DescriptorSetLayout.SamplerDescriptorSetLayout textureDescriptorSetLayout;
    private TextureSampler textureSampler;    
    private DescriptorSetLayout.UniformDescriptorSetLayout uniformDescriptorSetLayout;

    public ShadowRenderActivity(SwapChain swapChain, PipelineCache pipelineCache, Scene scene) {
        firstRun = true;
        this.swapChain = swapChain;
        this.scene = scene;
        device = swapChain.getDevice();
        int numImages = swapChain.getNumImages();
        shadowsFrameBuffer = new ShadowsFrameBuffer(device);
        createShaders();
        createDescriptorPool(numImages);
        createDescriptorSets(numImages);
        createPipeline(pipelineCache);
        createShadowCascades();
    }
    ...
}
```

As you can see, its definition is quite similar to the `GeometryRenderActivity` class, we define some constants for the shaders and several attributes to hold the descriptor sets, buffers, the pipeline, etc. In the constructor we create and instance of the frame buffer and call several methods to complete the initialization. Let's review those method by order of appearance. The `createShaders` method, just creates a new `ShaderProgram` which links a vertex, a geometry shader and a fragment shader:

```java
public class ShadowRenderActivity {
    ...
    private void createShaders() {
        EngineProperties engineProperties = EngineProperties.getInstance();
        if (engineProperties.isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(SHADOW_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader);
            ShaderCompiler.compileShaderIfChanged(SHADOW_GEOMETRY_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_geometry_shader);
            ShaderCompiler.compileShaderIfChanged(SHADOW_FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader);
        }
        shaderProgram = new ShaderProgram(device, new ShaderProgram.ShaderModuleData[]
                {
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, SHADOW_VERTEX_SHADER_FILE_SPV),
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_GEOMETRY_BIT, SHADOW_GEOMETRY_SHADER_FILE_SPV),
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, SHADOW_FRAGMENT_SHADER_FILE_SPV),
                });
    }
    ...
}
```

The `createDescriptorPool` should already be familiar to you, in this case we will be using just regular uniforms to hold the cascade splits information. We need to create separate buffers per swap chain image to avoid modifying the buffers while we are using them for rendering. We will also need uniforms to access texture information of the models (we need to check transparent fragments so they do not cast shadows).

```java
public class ShadowRenderActivity {
    ...
    private void createDescriptorPool(int numImages) {
        EngineProperties engineProps = EngineProperties.getInstance();
        List<DescriptorPool.DescriptorTypeCount> descriptorTypeCounts = new ArrayList<>();
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(numImages, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER));
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(engineProps.getMaxMaterials(), VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER));
        descriptorPool = new DescriptorPool(device, descriptorTypeCounts);
    }
    ...
}
```

The `createDescriptorSets` method just creates de required descriptor sets and the associated buffers used to pass uniform values. In our case, we will be using the projection view matrices buffers as uniforms to render the depth maps and the textures associated to the materials.

```java
public class ShadowRenderActivity {
    ...
    private void createDescriptorSets(int numImages) {
        uniformDescriptorSetLayout = new DescriptorSetLayout.UniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_GEOMETRY_BIT);
        textureDescriptorSetLayout = new DescriptorSetLayout.SamplerDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT);
        descriptorSetLayouts = new DescriptorSetLayout[]{
                uniformDescriptorSetLayout,
                textureDescriptorSetLayout,
        };

        descriptorSetMap = new HashMap<>();
        textureSampler = new TextureSampler(device, 1, false);
        projMatrixDescriptorSet = new DescriptorSet.UniformDescriptorSet[numImages];
        shadowsUniforms = new VulkanBuffer[numImages];
        for (int i = 0; i < numImages; i++) {
            shadowsUniforms[i] = new VulkanBuffer(device, (long)
                    GraphConstants.MAT4X4_SIZE * GraphConstants.SHADOW_MAP_CASCADE_COUNT,
                    VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
            projMatrixDescriptorSet[i] = new DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout,
                    shadowsUniforms[i], 0);
        }
    }
    ...
}
```

After that, we can create the pipeline:

```java
public class ShadowRenderActivity {
    ...
    private void createPipeline(PipelineCache pipelineCache) {
        Pipeline.PipeLineCreationInfo pipeLineCreationInfo = new Pipeline.PipeLineCreationInfo(
                shadowsFrameBuffer.getRenderPass().getVkRenderPass(), shaderProgram,
                GeometryAttachments.NUMBER_COLOR_ATTACHMENTS, true, true, GraphConstants.MAT4X4_SIZE,
                new VertexBufferStructure(), descriptorSetLayouts);
        pipeLine = new Pipeline(pipelineCache, pipeLineCreationInfo);
    }
    ...
}
```

Finally, we just initialize the list of cascade shadow instances that will be updated later on when calculating their projection matrices:

```java
public class ShadowRenderActivity {
    ...
    private void createShadowCascades() {
        cascadeShadows = new ArrayList<>();
        for (int i = 0; i < GraphConstants.SHADOW_MAP_CASCADE_COUNT; i++) {
            CascadeShadow cascadeShadow = new CascadeShadow();
            cascadeShadows.add(cascadeShadow);
        }
    }
    ...
}
```

The `ShadowRenderActivity` class defines also a `cleanup` method to free the resources and some getters to retrieve the depth attachment and the cascade shadows.

```java
public class ShadowRenderActivity {
    ...
    public void cleanup() {
        pipeLine.cleanup();
        Arrays.stream(shadowsUniforms).forEach(VulkanBuffer::cleanup);
        uniformDescriptorSetLayout.cleanup();
        textureDescriptorSetLayout.cleanup();
        textureSampler.cleanup();
        descriptorPool.cleanup();
        shaderProgram.cleanup();
        shadowsFrameBuffer.cleanup();
    }

    public Attachment getDepthAttachment() {
        return shadowsFrameBuffer.getDepthAttachment();
    }

    public List<CascadeShadow> getShadowCascades() {
        return cascadeShadows;
    }
    ...
}
```

Let's examine now the methods that render the scene to generate the depth maps, called `recordCommandBuffer` and `recordEntities`:

```java
public class ShadowRenderActivity {
    ...
    public void recordCommandBuffer(CommandBuffer commandBuffer, List<VulkanModel> vulkanModelList) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (firstRun || scene.isLightChanged() || scene.getCamera().isHasMoved()) {
                CascadeShadow.updateCascadeShadows(cascadeShadows, scene);
                if (firstRun) {
                    firstRun = false;
                }
            }

            int idx = swapChain.getCurrentFrame();

            updateProjViewBuffers(idx);

            VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
            clearValues.apply(0, v -> v.depthStencil().depth(1.0f));

            EngineProperties engineProperties = EngineProperties.getInstance();
            int shadowMapSize = engineProperties.getShadowMapSize();
            int width = shadowMapSize;
            int height = shadowMapSize;

            VkCommandBuffer cmdHandle = commandBuffer.getVkCommandBuffer();

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                    .x(0)
                    .y(height)
                    .height(-height)
                    .width(width)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(cmdHandle, 0, viewport);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack)
                    .extent(it -> it
                            .width(width)
                            .height(height))
                    .offset(it -> it
                            .x(0)
                            .y(0));
            vkCmdSetScissor(cmdHandle, 0, scissor);

            FrameBuffer frameBuffer = shadowsFrameBuffer.getFrameBuffer();

            VkRenderPassBeginInfo renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                    .renderPass(shadowsFrameBuffer.getRenderPass().getVkRenderPass())
                    .pClearValues(clearValues)
                    .renderArea(a -> a.extent().set(width, height))
                    .framebuffer(frameBuffer.getVkFrameBuffer());

            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeLine.getVkPipeline());

            LongBuffer descriptorSets = stack.mallocLong(2)
                    .put(0, projMatrixDescriptorSet[idx].getVkDescriptorSet());

            recordEntities(stack, cmdHandle, vulkanModelList, descriptorSets);

            vkCmdEndRenderPass(cmdHandle);
        }
    }

    private void recordEntities(MemoryStack stack, VkCommandBuffer cmdHandle, List<VulkanModel> vulkanModelList, LongBuffer descriptorSets) {
        LongBuffer offsets = stack.mallocLong(1);
        offsets.put(0, 0L);
        LongBuffer vertexBuffer = stack.mallocLong(1);
        for (VulkanModel vulkanModel : vulkanModelList) {
            String modelId = vulkanModel.getModelId();
            List<Entity> entities = scene.getEntitiesByModelId(modelId);
            if (entities.isEmpty()) {
                continue;
            }
            for (VulkanModel.VulkanMaterial material : vulkanModel.getVulkanMaterialList()) {
                TextureDescriptorSet textureDescriptorSet = descriptorSetMap.get(material.texture().getFileName());
                for (VulkanModel.VulkanMesh mesh : material.vulkanMeshList()) {
                    vertexBuffer.put(0, mesh.verticesBuffer().getBuffer());
                    vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                    vkCmdBindIndexBuffer(cmdHandle, mesh.indicesBuffer().getBuffer(), 0, VK_INDEX_TYPE_UINT32);

                    for (Entity entity : entities) {
                        descriptorSets.put(1, textureDescriptorSet.getVkDescriptorSet());
                        vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                                pipeLine.getVkPipelineLayout(), 0, descriptorSets, null);

                        setPushConstant(pipeLine, cmdHandle, entity.getModelMatrix());
                        vkCmdDrawIndexed(cmdHandle, mesh.numIndices(), 1, 0, 0, 0);
                    }
                }
            }
        }
    }
    ...
}
```

The methods are quite similar to the one used in the `GeometryRenderActivity` class, with the following differences:

- In this case, we receive a `CommandBuffer` instance as a parameter, instead of creating our command buffers for rendering the depth map. We will use the same command buffer used while doing the geometry pass to render the depth maps. Doing this way there is no need to add additionally synchronization code so the lighting phase starts when the scene and the depth maps have been properly render. We can do this, because rendering the geometry information and the depth maps are independent.
- Since we are using the geometry command buffer, we do not perform any reset operation over it and we do not need to provide a submit method. This will be done when submitting the geometry stage commands.
- We only update the projection view matrices of the cascade splits if the directional light has changed or the camera has moved. This way, we avoid performing this calculations for each frame if nothing changes.

We will need also a method, which will be called from the `Render` class to set up the texture descriptor sets (as in the `GeometryRenderActivity` class):
```java
public class ShadowRenderActivity {
    ...
    public void registerModels(List<VulkanModel> vulkanModelList) {
        device.waitIdle();
        for (VulkanModel vulkanModel : vulkanModelList) {
            for (VulkanModel.VulkanMaterial vulkanMaterial : vulkanModel.getVulkanMaterialList()) {
                updateTextureDescriptorSet(vulkanMaterial.texture());
            }
        }
    }
    ...
}
```

The rest of the methods of this class are for supporting resizing (we just store the reference to the new swap chain and update the cascades splits projection matrices), for setting the push constants (that will hold the model matrices) and for updating the uniform buffers that will contain the cascade splits projection view matrices.

```java
public class ShadowRenderActivity {
    ...
    public void resize(SwapChain swapChain) {
        this.swapChain = swapChain;
        CascadeShadow.updateCascadeShadows(cascadeShadows);
    }

    private void setPushConstant(Pipeline pipeLine, VkCommandBuffer cmdHandle, Matrix4f matrix) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pushConstantBuffer = stack.malloc(GraphConstants.MAT4X4_SIZE);
            matrix.get(0, pushConstantBuffer);
            vkCmdPushConstants(cmdHandle, pipeLine.getVkPipelineLayout(),
                    VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstantBuffer);
        }
    }

    private void updateProjViewBuffers(int idx) {
        int offset = 0;
        for (CascadeShadow cascadeShadow : cascadeShadows) {
            VulkanUtils.copyMatrixToBuffer(shadowsUniforms[idx], cascadeShadow.getProjViewMatrix(), offset);
            offset += GraphConstants.MAT4X4_SIZE;
        }
    }
    ...
}
```

The vertex shader (`shadow_vertex.glsl`) is quite simple, we just apply the model matrix, passed as a push constant, to transform the input coordinates. We need also to pass the texture coordinates to the next shader (geometry).

```glsl
#version 450

layout(location = 0) in vec3 entityPos;
layout(location = 1) in vec3 entityNormal;
layout(location = 2) in vec3 entityTangent;
layout(location = 3) in vec3 entityBitangent;
layout(location = 4) in vec2 entityTextCoords;

layout(push_constant) uniform matrices {
    mat4 modelMatrix;
} push_constants;

layout (location = 0) out vec2 outTextCoord;

void main()
{
    gl_Position = push_constants.modelMatrix * vec4(entityPos, 1.0f);
    outTextCoord = entityTextCoords;
}
```

The interesting part comes in the geometry shader (`shadow_geometry.glsl`):

```glsl
#version 450

// You should change this manually if GraphConstants.SHADOW_MAP_CASCADE_COUNT changes
#define SHADOW_MAP_CASCADE_COUNT 3

layout (triangles, invocations = SHADOW_MAP_CASCADE_COUNT) in;
layout (triangle_strip, max_vertices = 3) out;

layout (location = 0) in vec2 inTextCoords[];
layout (location = 0) out vec2 outTextCoords;

layout(set = 0, binding = 0) uniform ProjUniforms {
    mat4 projViewMatrices[SHADOW_MAP_CASCADE_COUNT];
} projUniforms;


void main()
{
    for (int i = 0; i < 3; i++)
    {
        outTextCoords = inTextCoords[i];
        gl_Layer = gl_InvocationID;
        gl_Position = projUniforms.projViewMatrices[gl_InvocationID] * gl_in[i].gl_Position;
        EmitVertex();
    }
    EndPrimitive();
}
```

First of all, unfortunately, we cannot pass the number of cascade splits as a specialization constant or as an uniform. The geometry shader will be instanced (please do not mix this with instanced rendering, which is a different concept), that is we will be generating multiple primitives (triangles) for a single input primitive. That is, the geometry shader will be executed, instanced, many times for each input triangle. This is controlled by the `invocations` parameter in the layout qualifier. This parameter requires a literal integer, therefore, we cannot use uniforms or specialization constants to pass the number of splits. Please keep this in mind if you want to modify that number, you will need to update the geometry shader manually. Taking all of this into consideration, the main method, contains a loop that will generate as many vertices as vertices has the input primitive multiplied by the number of invocations. For each of them, we will apply the projection view matrix associated to one of the cascade splits, storing that information in a specific layer of the depth image used as an output attachment. The geometry shader will be executed three times for each input primitive, therefore, a single triangle will generate three. 

If we do not want to use geometry shaders, we could get the same results using a fragment shader. In this case, however, we would need to record the commands to draw the scene items as many times as cascade splits we have. In this approach we would need also dedicated image views (one per split) to dump the results to a specific layer of the output attachment. In a fragment shader we cannot specify the layer where we should dump the results. In the geometry shader, we aer setting this by using the `gl_Layer` pre-built variable, which is assigned to the iteration of the geometry shader (from `0` to `invocations`).

Another important aspect is that we receive the texture coordinates in the `inTextCoords` input variable. This is declared as an array, which is mandatory for input variables in geometry shaders. We use that input variable to pass it to the fragment shader stage using the `outTextCoords` variable.

The fragment shader, `shadow_fragment.glsl`, is defined like this:
```glsl
#version 450

layout (set = 1, binding = 0) uniform sampler2D textSampler;
layout (location = 0) in vec2 inTextCoords;

void main()
{
    float alpha = texture(textSampler, inTextCoords).a;
    if (alpha < 0.5) {
        discard;
    }
}
```

As you can see, we use the texture coordinates to check the level of transparency of the fragment and discard the ones below `0.5`. By doing so, we will control that transparent fragments will not cast any shadow. Keep in mind that if you do not need to support transparent elements, you can remove the fragment shader, depth values would be generated correctly just form the output of the geometry shader. In this case, there is no need to have even an empty fragment shader. You can just remove it.

## Changes in geometry phase

Changes in the geometry pass are quite minimum, we need to modify the `GeometryFrameBuffer` class to use the new parameter of the `FrameBuffer` class which states the layers to be used. In this case, we are still using one layer images, so the change is quite simple.

```java
public class GeometryFrameBuffer {
    ...
    private void createFrameBuffer(SwapChain swapChain) {
        ...
            frameBuffer = new FrameBuffer(swapChain.getDevice(), geometryAttachments.getWidth(), geometryAttachments.getHeight(),
                    attachmentsBuff, geometryRenderPass.getVkRenderPass(), 1);
        ...
    }
    ...
}
```

We need also to modify the `GeometryRenderActivity` class, because the commands that record the drawing of t he scene will be shared with the shadow render pass. We need to split some of the code to allow that recording and jointly submit the recorded commands. We will first review a new method which will be called to begin the recording:

```java
public class GeometryRenderActivity {
    ...
    public CommandBuffer beginRecording() {
        int idx = swapChain.getCurrentFrame();

        Fence fence = fences[idx];
        CommandBuffer commandBuffer = commandBuffers[idx];

        fence.fenceWait();
        fence.reset();

        commandBuffer.reset();
        commandBuffer.beginRecording();

        return commandBuffer;
    }
    ...
}
```

The code in the `beginRecording` method was previously contained in the `recordCommandBuffer`. Since we will be sharing the commands, we need to split the recording of the drawing commands with the drawing commands themselves. Therefore, we need also a `endRecording` method:

```java
public class GeometryRenderActivity {
    ...
    public void endRecording(CommandBuffer commandBuffer) {
        commandBuffer.endRecording();
    }
    ...
}
```

As a result, the `recordCommandBuffer` method needs to be modified to remove the code that starts and finalizes the recording. This method will received now a `CommandBuffer` as a parameter, which will be used for the recording, but the rest is the same:

```java
public class GeometryRenderActivity {
    ...
    public void recordCommandBuffer(CommandBuffer commandBuffer, List<VulkanModel> vulkanModelList) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
            int width = swapChainExtent.width();
            int height = swapChainExtent.height();
            int idx = swapChain.getCurrentFrame();

            FrameBuffer frameBuffer = geometryFrameBuffer.getFrameBuffer();
            List<Attachment> attachments = geometryFrameBuffer.geometryAttachments().getAttachments();
            VkClearValue.Buffer clearValues = VkClearValue.calloc(attachments.size(), stack);
            for (Attachment attachment : attachments) {
                if (attachment.isDepthAttachment()) {
                    clearValues.apply(v -> v.depthStencil().depth(1.0f));
                } else {
                    clearValues.apply(v -> v.color().float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 1));
                }
            }
            clearValues.flip();

            VkRenderPassBeginInfo renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                    .renderPass(geometryFrameBuffer.getRenderPass().getVkRenderPass())
                    .pClearValues(clearValues)
                    .renderArea(a -> a.extent().set(width, height))
                    .framebuffer(frameBuffer.getVkFrameBuffer());

            VkCommandBuffer cmdHandle = commandBuffer.getVkCommandBuffer();
            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeLine.getVkPipeline());

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                    .x(0)
                    .y(height)
                    .height(-height)
                    .width(width)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(cmdHandle, 0, viewport);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack)
                    .extent(it -> it
                            .width(width)
                            .height(height))
                    .offset(it -> it
                            .x(0)
                            .y(0));
            vkCmdSetScissor(cmdHandle, 0, scissor);

            LongBuffer descriptorSets = stack.mallocLong(6)
                    .put(0, projMatrixDescriptorSet.getVkDescriptorSet())
                    .put(1, viewMatricesDescriptorSets[idx].getVkDescriptorSet())
                    .put(5, materialsDescriptorSet.getVkDescriptorSet());
            VulkanUtils.copyMatrixToBuffer(viewMatricesBuffer[idx], scene.getCamera().getViewMatrix());

            recordEntities(stack, cmdHandle, descriptorSets, vulkanModelList);

            vkCmdEndRenderPass(cmdHandle);
        }
    }
    ...
}
```

## Changes in light phase

The phase where we apply the lighting needs more changes, we need to put the shadows map into work here. We will start with a pretty straight forward change, we need to modify the `LightingFrameBuffer` class to meet the changes in the `FrameBuffer` class which require us to specify the number of layers:

```java
public class LightingFrameBuffer {
    ...
    private void createFrameBuffers(SwapChain swapChain) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkExtent2D extent2D = swapChain.getSwapChainExtent();
            int width = extent2D.width();
            int height = extent2D.height();

            int numImages = swapChain.getNumImages();
            frameBuffers = new FrameBuffer[numImages];
            LongBuffer attachmentsBuff = stack.mallocLong(1);
            for (int i = 0; i < numImages; i++) {
                attachmentsBuff.put(0, swapChain.getImageViews()[i].getVkImageView());
                frameBuffers[i] = new FrameBuffer(swapChain.getDevice(), width, height,
                        attachmentsBuff, lightingRenderPass.getVkRenderPass(), 1);
            }
        }
    }
    ...
}
```

Prior to reviewing the changes in the `LightingRenderActivity` class, we will examine the changes in the shaders so we can better understand the modifications required in that class. The vertex shader (`lighting_vertex.glsl`) does not need to be modified at all, the changes will affect the fragment shader (`lighting_fragment.glsl`). Let's dissect the changes. First, we will define a new set of specialization constants:

```glsl
...
layout (constant_id = 1) const int SHADOW_MAP_CASCADE_COUNT = 3;
layout (constant_id = 2) const int USE_PCF = 0;
layout (constant_id = 3) const float BIAS = 0.0005;
layout (constant_id = 4) const int DEBUG_SHADOWS = 0;
...
const float SHADOW_FACTOR = 0.25;
...
```

Description of the constants:

- `SHADOW_MAP_CASCADE_COUNT`: It will hold the number of splits we are going to have. 
- `USE_PCF`: It will control if we apply Percentage Closer Filter (`1`) or not (`0`) to the shadows.
- `BIAS`: The depth bias to apply when estimating if a fragment is affected by a shadow or not. This is used to reduce shadow artifacts, such as shadow acne.
- `DEBUG_SHADOWS`: This will control if we apply a color to the fragments to identify the cascade split to which they will assigned (it will need to have the value `1` to activate this).

We will need also to pass to the shader the inverse view matrix. In previous chapter, we used the inverse of the projection matrix to get the fragment position in view coordinates. In this case, we need to go a step beyond and get the fragment position also in world coordinates, if we multiply the inverse view matrix by the fragment position in view coordinates we will get the world coordinates. In addition to that, we need the projection view matrices of the cascade splits as well as their split distances:

```glsl
...
struct CascadeShadow {
    mat4 projViewMatrix;
    vec4 splitDistance;
};
...
layout(set = 2, binding = 0) uniform ProjUniform {
    mat4 invProjectionMatrix;
    mat4 invViewMatrix;
} projUniform;

layout(set = 3, binding = 0) uniform ShadowsUniforms {
    CascadeShadow cascadeshadows[SHADOW_MAP_CASCADE_COUNT];
} shadowsUniforms;
```

We will create a new function, named `calcShadow`, which given a world position an a cascade split index, will return a shadow factor that will be applied to the final fragment color. If the fragment is not affected by a shadow, the result will be `1`, it will not affect the final color:
```glsl
float calcShadow(vec4 worldPosition, uint cascadeIndex)
{
    vec4 shadowMapPosition = shadowsUniforms.cascadeshadows[cascadeIndex].projViewMatrix * worldPosition;

    float shadow = 1.0;
    vec4 shadowCoord = shadowMapPosition / shadowMapPosition.w;
    shadowCoord.x = shadowCoord.x * 0.5 + 0.5;
    shadowCoord.y = (-shadowCoord.y) * 0.5 + 0.5;
    
    if (USE_PCF == 1) {
        shadow = filterPCF(shadowCoord, cascadeIndex);
    } else {
        shadow = textureProj(shadowCoord, vec2(0, 0), cascadeIndex);
    }
    return shadow;

}
```
This function, transforms from world coordinates space to the NDC space of the directional light, for a specific cascade split, using its ortographic projection. That is, we multiply world space by the projection view matrix of the specified cascade split. After that, we need to transform those coordinates to texture coordinates (that is in the range [0, 1], starting at the top left corner). With that information, we can apply PCF or not. If not, we will call the `textureProj` function which just calculates the shadow factor without applying any filtering and is defined like this:
```glsl
float textureProj(vec4 shadowCoord, vec2 offset, uint cascadeIndex)
{
    float shadow = 1.0;

    if (shadowCoord.z > -1.0 && shadowCoord.z < 1.0) {
        float dist = texture(shadowSampler, vec3(shadowCoord.st + offset, cascadeIndex)).r;
        if (shadowCoord.w > 0 && dist < shadowCoord.z - BIAS) {
            shadow = SHADOW_FACTOR;
        }
    }
    return shadow;
}
```

This function just samples the depth maps, generated previously, with the texture coordinates and setting the layer associated to the proper cascade index. If the retrieved depth value is lower than the fragment `z` value , this will mean that this fragment is in a shadow. This function receives an `offset` parameter which purpose will be understood when examining the `filterPCF` function:

```glsl
float filterPCF(vec4 sc, uint cascadeIndex)
{
    ivec2 texDim = textureSize(shadowSampler, 0).xy;
    float scale = 0.75;
    float dx = scale * 1.0 / float(texDim.x);
    float dy = scale * 1.0 / float(texDim.y);

    float shadowFactor = 0.0;
    int count = 0;
    int range = 1;

    for (int x = -range; x <= range; x++) {
        for (int y = -range; y <= range; y++) {
            shadowFactor += textureProj(sc, vec2(dx*x, dy*y), cascadeIndex);
            count++;
        }
    }
    return shadowFactor / count;
}
```

This purpose of this function to return an average shadow factor calculated using the values obtained from the fragments that surround the current one. It just calculates this, retrieving the shadow factor for each of them, calling the `textureProj` function, by passing an offset that will be used when sampling the shadow map.

In the `main` function, taking as an input the view position, we get the world position by applying the inverse view matrix, with that information, we iterate over the split distances, calculated for each cascade split, to determine the cascade index that this fragment belongs to and calculate the shadow factor:

```glsl
void main() {
    ...
    // Retrieve position from depth
    vec4 clip       = vec4(inTextCoord.x * 2.0 - 1.0, inTextCoord.y * -2.0 + 1.0, texture(depthSampler, inTextCoord).x, 1.0);
    vec4 view_w     = projUniform.invProjectionMatrix * clip;
    vec3 view_pos   = view_w.xyz / view_w.w;
    vec4 world_pos    = projUniform.invViewMatrix * vec4(view_pos, 1);

    uint cascadeIndex = 0;
    for (uint i = 0; i < SHADOW_MAP_CASCADE_COUNT - 1; ++i) {
        if (view_pos.z < shadowsUniforms.cascadeshadows[i].splitDistance.x) {
            cascadeIndex = i + 1;
        }
    }

    float shadowFactor = calcShadow(world_pos, cascadeIndex);
    ...
}
```

The final fragment color is modulated by the shadow factor. Finally, if the debug mode is activated we apply a color to that fragment to identify the cascades we are using:

```glsl
void main() {
    ...
    outFragColor = vec4(pow(ambient * shadowFactor + lightColor * shadowFactor, vec3(0.4545)), 1.0);

    if (DEBUG_SHADOWS == 1) {
        switch (cascadeIndex) {
            case 0:
            outFragColor.rgb *= vec3(1.0f, 0.25f, 0.25f);
            break;
            case 1:
            outFragColor.rgb *= vec3(0.25f, 1.0f, 0.25f);
            break;
            case 2:
            outFragColor.rgb *= vec3(0.25f, 0.25f, 1.0f);
            break;
            default :
            outFragColor.rgb *= vec3(1.0f, 1.0f, 0.25f);
            break;
        }
    }
}
```

Now we can examine the changes in the `LightingRenderActivity` class. First, we need a uniform that will hold the inverse projection and view matrices. Previously, we had just one buffer, because it only contained the inverse projection matrix. Since this did not change between frames we just needed one buffer. However, now, it will store also the inverse view matrix. That matrix can change between frame, so to avoid modifying the buffer while rendering, we will have as many buffers as swap chain images. We will need also new buffers, and descriptor sets for the cascade shadow splits data. We will not update that uniform in the constructor, but while recording the commands, therefore the constructor has been changed (no `Scene` instance as a parameter) and the `updateInvProjMatrix` method has been removed. The previous attributes `invProjBuffer` and `invProjMatrixDescriptorSet` have been removed. We need also new uniforms for the data of the cascade splits projection view uniforms and cascade instances). In the `cleanup` method, we just need to free those resources.

```java
public class LightingRenderActivity {
    ...
    private VulkanBuffer[] invMatricesBuffers;
    private DescriptorSet.UniformDescriptorSet[] invMatricesDescriptorSets;
    ...
    private VulkanBuffer[] shadowsMatricesBuffers;
    private DescriptorSet.UniformDescriptorSet[] shadowsMatricesDescriptorSets;
    ...
    public LightingRenderActivity(SwapChain swapChain, CommandPool commandPool, PipelineCache pipelineCache,
                                  List<Attachment> attachments, Scene scene) {
        this.swapChain = swapChain;
        this.scene = scene;
        device = swapChain.getDevice();
        auxVec = new Vector4f();
        lightSpecConstants = new LightSpecConstants();

        lightingFrameBuffer = new LightingFrameBuffer(swapChain);
        int numImages = swapChain.getNumImages();
        createShaders();
        createDescriptorPool(attachments);
        createUniforms(numImages);
        createDescriptorSets(attachments, numImages);
        createPipeline(pipelineCache);
        createCommandBuffers(commandPool, numImages);

        for (int i = 0; i < numImages; i++) {
            preRecordCommandBuffer(i);
        }
    }

    public void cleanup() {
        ...
        Arrays.stream(invMatricesBuffers).forEach(VulkanBuffer::cleanup);
        ...
        Arrays.stream(shadowsMatricesBuffers).forEach(VulkanBuffer::cleanup);
        ...
    }
    ...
}
```

Since we have switched now from one uniform (that held the inverse projection matrix) to an array (one per swap chain image), an created new uniforms for the cascade shadow splits (also one per swap chain image), we need to update the total number of uniform descriptors we will need when creating the descriptor pool. This affects also to the descriptor set creation and the buffers that will hold the data for those uniforms.

```java
public class LightingRenderActivity {
    ...
    private void createDescriptorPool(List<Attachment> attachments) {
        ...
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(swapChain.getNumImages() * 3, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER));
        ...
    }

    private void createDescriptorSets(List<Attachment> attachments, int numImages) {
        attachmentsLayout = new AttachmentsLayout(device, attachments.size());
        uniformDescriptorSetLayout = new DescriptorSetLayout.UniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT);
        descriptorSetLayouts = new DescriptorSetLayout[]{
                attachmentsLayout,
                uniformDescriptorSetLayout,
                uniformDescriptorSetLayout,
                uniformDescriptorSetLayout,
        };

        attachmentsDescriptorSet = new AttachmentsDescriptorSet(descriptorPool, attachmentsLayout,
                attachments, 0);

        lightsDescriptorSets = new DescriptorSet.UniformDescriptorSet[numImages];
        invMatricesDescriptorSets = new DescriptorSet.UniformDescriptorSet[numImages];
        shadowsMatricesDescriptorSets = new DescriptorSet.UniformDescriptorSet[numImages];
        for (int i = 0; i < numImages; i++) {
            lightsDescriptorSets[i] = new DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout,
                    lightsBuffers[i], 0);
            invMatricesDescriptorSets[i] = new DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout,
                    invMatricesBuffers[i], 0);
            shadowsMatricesDescriptorSets[i] = new DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout,
                    shadowsMatricesBuffers[i], 0);
        }
    }    
    ...
    private void createUniforms(int numImages) {
        lightsBuffers = new VulkanBuffer[numImages];
        invMatricesBuffers = new VulkanBuffer[numImages];
        shadowsMatricesBuffers = new VulkanBuffer[numImages];
        for (int i = 0; i < numImages; i++) {
            lightsBuffers[i] = new VulkanBuffer(device, (long)
                    GraphConstants.INT_LENGTH * 4 + GraphConstants.VEC4_SIZE * 2 * GraphConstants.MAX_LIGHTS +
                    GraphConstants.VEC4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);

            invMatricesBuffers[i] = new VulkanBuffer(device, (long)
                    GraphConstants.MAT4X4_SIZE * 2, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);

            shadowsMatricesBuffers[i] = new VulkanBuffer(device, (long)
                    (GraphConstants.MAT4X4_SIZE + GraphConstants.VEC4_SIZE) * GraphConstants.SHADOW_MAP_CASCADE_COUNT,
                    VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
        }
    }
    ...
}
```

Those descriptor sets need to be bound while rendering, and its data filled up for each frame:

```java
public class LightingRenderActivity {
    ...
    public void preRecordCommandBuffer(int idx) {
        ...
            LongBuffer descriptorSets = stack.mallocLong(4)
                    .put(0, attachmentsDescriptorSet.getVkDescriptorSet())
                    .put(1, lightsDescriptorSets[idx].getVkDescriptorSet())
                    .put(2, invMatricesDescriptorSets[idx].getVkDescriptorSet())
                    .put(3, shadowsMatricesDescriptorSets[idx].getVkDescriptorSet());
        ...
    }

    public void prepareCommandBuffer(List<CascadeShadow> cascadeShadows) {
        ...
        updateLights(scene.getAmbientLight(), scene.getLights(), scene.getCamera().getViewMatrix(), lightsBuffers[idx]);
        updateInvMatrices(scene, invMatricesBuffers[idx]);
        updateCascadeShadowMatrices(cascadeShadows, shadowsMatricesBuffers[idx]);
    }    
    ...
}
```

Because of the changes described above, in the `resize`method we will not be updating the inverse projection matrix anymore:

```java
public class LightingRenderActivity {
    ...
    public void resize(SwapChain swapChain, List<Attachment> attachments) {
        this.swapChain = swapChain;
        attachmentsDescriptorSet.update(attachments);
        lightingFrameBuffer.resize(swapChain);

        int numImages = swapChain.getNumImages();
        for (int i = 0; i < numImages; i++) {
            preRecordCommandBuffer(i);
        }
    }
    ...
}
```

The only pending changes for the `LightingRenderActivity` are the ones that populate the buffers associated to the new uniforms:

```java
public class LightingRenderActivity {
    ...
    private void updateCascadeShadowMatrices(List<CascadeShadow> cascadeShadows, VulkanBuffer shadowsUniformBuffer) {
        long mappedMemory = shadowsUniformBuffer.map();
        ByteBuffer buffer = MemoryUtil.memByteBuffer(mappedMemory, (int) shadowsUniformBuffer.getRequestedSize());
        int offset = 0;
        for (CascadeShadow cascadeShadow : cascadeShadows) {
            cascadeShadow.getProjViewMatrix().get(offset, buffer);
            buffer.putFloat(offset + GraphConstants.MAT4X4_SIZE, cascadeShadow.getSplitDistance());
            offset += GraphConstants.MAT4X4_SIZE + GraphConstants.VEC4_SIZE;
        }
        shadowsUniformBuffer.unMap();
    }

    private void updateInvMatrices(Scene scene, VulkanBuffer invMatricesBuffer) {
        Matrix4f invProj = new Matrix4f(scene.getProjection().getProjectionMatrix()).invert();
        Matrix4f invView = new Matrix4f(scene.getCamera().getViewMatrix()).invert();
        VulkanUtils.copyMatrixToBuffer(invMatricesBuffer, invProj, 0);
        VulkanUtils.copyMatrixToBuffer(invMatricesBuffer, invView, GraphConstants.MAT4X4_SIZE);
    }
    ...
}
```

The `LightSpecConstants` class needs also to be updated to pass the values for the `SHADOW_MAP_CASCADE_COUNT`, `USE_PCF`, `BIAS` and `DEBUG_SHADOWS` constants.

```java
package org.vulkanb.eng.graph.lighting;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.vk.GraphConstants;

import java.nio.ByteBuffer;

public class LightSpecConstants {

    private ByteBuffer data;

    private VkSpecializationMapEntry.Buffer specEntryMap;
    private VkSpecializationInfo specInfo;

    public LightSpecConstants() {
        EngineProperties engineProperties = EngineProperties.getInstance();
        data = MemoryUtil.memAlloc(GraphConstants.INT_LENGTH * 4 + GraphConstants.FLOAT_LENGTH);
        data.putInt(GraphConstants.MAX_LIGHTS);
        data.putInt(GraphConstants.SHADOW_MAP_CASCADE_COUNT);
        data.putInt(engineProperties.isShadowPcf() ? 1 : 0);
        data.putFloat(engineProperties.getShadowBias());
        data.putInt(engineProperties.isShadowDebug() ? 1 : 0);
        data.flip();

        specEntryMap = VkSpecializationMapEntry.calloc(5);
        specEntryMap.get(0)
                .constantID(0)
                .size(GraphConstants.INT_LENGTH)
                .offset(0);
        specEntryMap.get(1)
                .constantID(1)
                .size(GraphConstants.INT_LENGTH)
                .offset(GraphConstants.INT_LENGTH);
        specEntryMap.get(2)
                .constantID(2)
                .size(GraphConstants.INT_LENGTH)
                .offset(GraphConstants.INT_LENGTH * 2);
        specEntryMap.get(3)
                .constantID(3)
                .size(GraphConstants.FLOAT_LENGTH)
                .offset(GraphConstants.INT_LENGTH * 3);
        specEntryMap.get(4)
                .constantID(4)
                .size(GraphConstants.INT_LENGTH)
                .offset(GraphConstants.INT_LENGTH * 3 + GraphConstants.FLOAT_LENGTH);

        specInfo = VkSpecializationInfo.calloc();
        specInfo.pData(data)
                .pMapEntries(specEntryMap);
    }

    public void cleanup() {
        MemoryUtil.memFree(specEntryMap);
        specInfo.free();
        MemoryUtil.memFree(data);
    }

    public VkSpecializationInfo getSpecInfo() {
        return specInfo;
    }
}
```

## Changes in the rest of the code

It is the turn now to view the changes in the rest of the code base required to support shadows rendering. Starting by the `Render` class, we need to create an instance of the `ShadowRenderActivity` class, free its resources when they are no longer required, and get the reference to the depth attachment so it can be sampled during lighting phase:

```java
public class Render {
    ...
    private ShadowRenderActivity shadowRenderActivity;
    ...
    public Render(Window window, Scene scene) {
        ...
        shadowRenderActivity = new ShadowRenderActivity(swapChain, pipelineCache);
        List<Attachment> attachments = new ArrayList<>();
        attachments.addAll(geometryRenderActivity.getAttachments());
        attachments.add(shadowRenderActivity.getDepthAttachment());
        lightingRenderActivity = new LightingRenderActivity(swapChain, commandPool, pipelineCache, attachments);
        ...
    }

    public void cleanup() {
        ...
        shadowRenderActivity.cleanup();
        ...
    }
    ...
}
```

The `render` method is also modified so the command buffer can be shared between the `GeometryRenderActivity` and the `ShadowRenderActivity` classes. The `resize` method is also changed to update the `ShadowRenderActivity` class and take into account the depth attachment.

```java
public class Render {
    ...
    public void render(Window window, Scene scene) {
        ...
        CommandBuffer commandBuffer = geometryRenderActivity.beginRecording();
        geometryRenderActivity.recordCommandBuffer(commandBuffer, vulkanModels);
        shadowRenderActivity.recordCommandBuffer(commandBuffer, vulkanModels);
        geometryRenderActivity.endRecording(commandBuffer);
        geometryRenderActivity.submit(graphQueue);
        lightingRenderActivity.prepareCommandBuffer(shadowRenderActivity.getShadowCascades());
        lightingRenderActivity.submit(graphQueue);

        if (swapChain.presentImage(graphQueue)) {
            window.setResized(true);
        }
    }

    private void resize(Window window) {
        ...
        geometryRenderActivity.resize(swapChain);
        shadowRenderActivity.resize(swapChain);
        List<Attachment> attachments = new ArrayList<>();
        attachments.addAll(geometryRenderActivity.getAttachments());
        attachments.add(shadowRenderActivity.getDepthAttachment());
        lightingRenderActivity.resize(swapChain, attachments);
    }
    ...
}
```

The `Camera` class defines now a new attribute that will be flag when the camera moves:

```java
public class Camera {
    ...
    private boolean hasMoved;
    ...
    public boolean isHasMoved() {
        return hasMoved;
    }
    ...
    private void recalculate() {
        hasMoved = true;
        ...
    }

    public void setHasMoved(boolean hasMoved) {
        this.hasMoved = hasMoved;
    }
    ...
}
```

In the `Scene` class we need to provide a way to get a reference to the directional light and to track if that light has changed to update the shadow maps accordingly:

```java
public class Scene {
    ...
    private Light directionalLight;
    ...
    private boolean lightChanged;
    ...
    public Light getDirectionalLight() {
        return directionalLight;
    }
    ...
    public boolean isLightChanged() {
        return lightChanged;
    }
    ...
    public void setLightChanged(boolean lightChanged) {
        this.lightChanged = lightChanged;
    }

    public void setLights(Light[] lights) {
        directionalLight = null;
        int numLights = lights != null ? lights.length : 0;
        if (numLights > GraphConstants.MAX_LIGHTS) {
            throw new RuntimeException("Maximum number of lights set to: " + GraphConstants.MAX_LIGHTS);
        }
        this.lights = lights;
        Optional<Light> option = Arrays.stream(lights).filter(l -> l.getPosition().w == 0).findFirst();
        if (option.isPresent()) {
            directionalLight = option.get();
        }

        lightChanged = true;
    }
}
```

In the `Engine` class, we will reset the camera moved flag prior to getting the input:

```java
public class Engine {
    ...
    public void run() {
        ...
        while (running && !window.shouldClose()) {
            scene.getCamera().setHasMoved(false);
            ...
        }
        ...
    }
    ...
}
```

The `EngineProperties` class needs also to be updated to read the additional properties that control depth map generation:

```java
public class EngineProperties {
    ...
    private static final float DEFAULT_SHADOW_BIAS = 0.00005f;
    private static final int DEFAULT_SHADOW_MAP_SIZE = 2048;
    ...
    private float shadowBias;
    private boolean shadowDebug;
    private int shadowMapSize;
    private boolean shadowPcf;
    ...
    private EngineProperties() {
        ...
            shadowPcf = Boolean.parseBoolean(props.getOrDefault("shadowPcf", false).toString());
            shadowBias = Float.parseFloat(props.getOrDefault("shadowBias", DEFAULT_SHADOW_BIAS).toString());
            shadowMapSize = Integer.parseInt(props.getOrDefault("shadowMapSize", DEFAULT_SHADOW_MAP_SIZE).toString());
            shadowDebug = Boolean.parseBoolean(props.getOrDefault("shadowDebug", false).toString());
        ...
    }
    ...
    public float getShadowBias() {
        return shadowBias;
    }

    public int getShadowMapSize() {
        return shadowMapSize;
    }
    ...
    public boolean isShadowDebug() {
        return shadowDebug;
    }

    public boolean isShadowPcf() {
        return shadowPcf;
    }
    ...
}
```

In the `Main` class we have just take care of setting the flag that signals that light has changed while handling the input and removed the green point light (we have also modified the starting angle to avoid problems in the calculations):

```java
public class Main implements IAppLogic {
    ...
    private float lightAngle = 90.1f;
    ...
    public void input(Window window, Scene scene, long diffTimeMillis) {
        ...
        if (window.isKeyPressed(GLFW_KEY_LEFT)) {
            angleInc -= 0.05f;
            scene.setLightChanged(true);
        } else if (window.isKeyPressed(GLFW_KEY_RIGHT)) {
            angleInc += 0.05f;
            scene.setLightChanged(true);
        } else {
            angleInc = 0;
            scene.setLightChanged(false);
        }
        ...
    }
    ...
}
```

Finally, to complete all the changes, we have to enable depth clamp to avoid near plane clipping and also enable support for geometry shaders:
```java
public class Device {
    ...
    public Device(Instance instance, PhysicalDevice physicalDevice) {
        ...
            // Set up required features
            VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.calloc(stack);
            VkPhysicalDeviceFeatures supportedFeatures = this.physicalDevice.getVkPhysicalDeviceFeatures();
            samplerAnisotropy = supportedFeatures.samplerAnisotropy();
            if (samplerAnisotropy) {
                features.samplerAnisotropy(true);
            }
            features.depthClamp(supportedFeatures.depthClamp());
            features.geometryShader(true);
        ...
    }
    ...
}
```

We are now done with the changes, you should now be able to see the scene with shadows applied, as in the following screenshot (you can move also light direction with left and right arrows):

<img src="screen-shot.png" title="" alt="Screen Shot" data-align="center">

As a bonus, you can try to activate the cascade shadow debug to show the cascade splits (parameters have been tweaked to better show the splits):

<img src="screen-shot-debug.png" title="" alt="Screen Shot (Debug)" data-align="center">

[Next chapter](../chapter-14/chapter-14.md)