# Going 3D

In this chapter we will be rendering a 3D model (a rotating cube). Although our models did have a z-coordinate, the output attachments we were using only dealt with colors, depth values were not handled at all. We need to define a new attachment to store depth information, a depth buffer. In addition to that, we will add support for window resizing.
You can find the complete source code for this chapter [here](../../booksamples/chapter-07).

## Depth Image

The first thing we must do is to create a depth image. In the case of the swap chain, images were already there, but in this case we need to allocate by ourselves. In order to handle that task we will create a new class named `Image`. The constructor starts like this:

```java
public Image(Device device, int width, int height, int format, int usage, int mipLevels, int sampleCount) {
    this.device = device;
    try (MemoryStack stack = MemoryStack.stackPush()) {
        this.format = format;
        this.mipLevels = mipLevels;

        VkImageCreateInfo imageCreateInfo = VkImageCreateInfo.callocStack(stack)
               .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
               .imageType(VK_IMAGE_TYPE_2D)
               .format(format)
               .extent(it -> it
                        .width(width)
                        .height(height)
                        .depth(1)
               )
               .mipLevels(mipLevels)
               .arrayLayers(1)
               .samples(sampleCount)
               .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
               .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
               .tiling(VK_IMAGE_TILING_OPTIMAL)
               .usage(usage);
```

The constructor receives the following parameters:

- `width` and `height`: The size of the image.

- `format`:  Describes the format of the texel blocks that compose the image.

- `usage`:  Describes the intended usage that this image will have.

- `mipLevels`:  Describes the levels of detail available for this image (more on this in later chapters).

- `sampleCount`:  Specifies the number of texels per sample (more on this in later chapters).

Besides storing some parameters as attributes of the class, the first thing we do is create a structure required to create an image named `VkImageCreateInfo`. The attributes that we are using are:

- `sType`:  The classical type of the structure. In this case we use the value `VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO`.

- `imageType`:  It specifies the dimensions of the the image. In our case we will be using regular 2D dimensions, so we set to the value:  `VK_IMAGE_TYPE_2D`.  Three dimensions images (`VK_IMAGE_TYPE_3D`) are like a set of slices of 2D textures and are used for volumetric effects or for scientific or medical visualizations (like MRIs).  One dimension textures are define by the value `VK_IMAGE_TYPE_1D`.

- `format`: Described above, format of the texel blocks that compose the image.

- `extent`:  It is the size of the image. In this case, the structure needs to support 3D images, so it includes the depth. For 2D images we just set it to `1`.

- `mipLevels`:  Already described in the constructor's parameters description.

- `arrayLayers`:  Images can be an array of layers. This is different than a 3D image. A 3D image contains data that is referred to the three axis. An array of layers are a set of 2D images indexed by a layer number. In our case we will just set it to `1`. 

- `samples`: Already described in the constructor's parameters description (`campleCount`).

- `initialLayout`:  This is the initial layout of the image. We just set it to `VK_IMAGE_LAYOUT_UNDEFINED` . If a transition to another layout is required it will need to be done later on. (This depends on the use case for the image, this is why we do not perform the transition here).

- `sharingMode`: It specifies if this resource will be shared by more than a single queue family at a time (`VK_SHARING_MODE_CONCURRENT`) or not (`VK_SHARING_MODE_EXCLUSIVE`).

- `tiling`: It specifies the tiling arrangement of the texel blocks in memory.  In our case, we chose to use the optimal value, so that the texels are laid out in the best format for each GPU.

- `usage`:  Already described in the constructor's parameters description.

Now we can create the image by invoking the `vkCreateImage` Vulkan function:

```java
        LongBuffer lp = stack.mallocLong(1);
        vkCheck(vkCreateImage(device.getVkDevice(), imageCreateInfo, null, lp), "Failed to create image");
        this.vkImage = lp.get(0);
```

Then we need to allocate the memory associated to that image. As in the case of buffers, that image is just a handle, we need to manually allocate the memory that will host the contents for the image by ourselves.  The first step is to get the memory requirements by calling the `vkGetImageMemoryRequirements` function:

```java
        // Get memory requirements for this object
        VkMemoryRequirements memReqs = VkMemoryRequirements.callocStack(stack);
        vkGetImageMemoryRequirements(device.getVkDevice(), this.getVkImage(), memReqs);
```

With that information we can populate the `VkMemoryAllocateInfo` structure which contains the information to perform the memory allocation.

```java
        // Select memory size and type
        VkMemoryAllocateInfo memAlloc = VkMemoryAllocateInfo.callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memReqs.size())
                .memoryTypeIndex(VulkanUtils.memoryTypeFromProperties(device.getPhysicalDevice(),
                        memReqs.memoryTypeBits(), 0));
```

Again, the code is similar as the one used with the buffers, once we have got the requirements, we set the memory size and select the adequate memory type index (obtained by calling the `memoryTypeFromProperties` from the `VulkanUtils` class). After that we can finally allocate the memory and bind it to the image:

```java
        // Allocate memory
        vkCheck(vkAllocateMemory(device.getVkDevice(), memAlloc, null, lp), "Failed to allocate memory");
        this.vkMemory = lp.get(0);

        // Bind memory
        vkCheck(vkBindImageMemory(device.getVkDevice(), this.getVkImage(), this.getVkMemory(), 0),
                "Failed to bind image memory");
    }
}
```

The rest of the methods of this class are the `cleanUp` to free resources and some *getters* to obtain the image handle, the associated memory, the format of the image and the mip levels.

```java
public void cleanUp() {
    vkDestroyImage(this.device.getVkDevice(), this.vkImage, null);
    vkFreeMemory(this.device.getVkDevice(), this.vkMemory, null);
}

public int getFormat() {
    return this.format;
}

public int getMipLevels() {
    return mipLevels;
}

public long getVkImage() {
    return this.vkImage;
}

public long getVkMemory() {
    return this.vkMemory;
}
```

## Changing vertices structure

In the previous chapter, we defined the structure of our vertices, which basically stated that our vertices were composed by x, y and z positions. Therefore, we would not need anything more to display 3D models. However, displaying a 3D model just using a single color (without shadows or light effects), makes difficult to verify if the model is being loaded property.  So, we will add extra components that we will reuse in next chapters, we will add texture coordinates. Although we will not be handling textures in this chapter, we can use those components to pass some color information (at lest for two color channels). We need to modify the `VertexBufferStructure`  in this way:

```java
public class VertexBufferStructure {

    public static final int TEXT_COORD_COMPONENTS = 2;
    private static final int NUMBER_OF_ATTRIBUTES = 2;
...
    public VertexBufferStructure() {
        this.viAttrs = VkVertexInputAttributeDescription.calloc(NUMBER_OF_ATTRIBUTES);
        this.viBindings = VkVertexInputBindingDescription.calloc(1);
        this.vi = VkPipelineVertexInputStateCreateInfo.calloc();

        int i = 0;
        // Position
        this.viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(0);

        // Texture coordinates
        i++;
        this.viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(POSITION_COMPONENTS * GraphConstants.FLOAT_LENGTH);

        this.viBindings.get(0)
                .binding(0)
                .stride(POSITION_COMPONENTS * GraphConstants.FLOAT_LENGTH +
                        TEXT_COORD_COMPONENTS * GraphConstants.FLOAT_LENGTH)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

        this.vi
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexBindingDescriptions(this.viBindings)
                .pVertexAttributeDescriptions(this.viAttrs);
    }
...
}
```

We define a new constant named `TEXT_COORD_COMPONENTS`  which states that the texture coordinates will be composed by two elements (two floats). The number of attributes of each vertex will be now two (defined by the constant `NUMBER_OF_ATTRIBUTES`), one for the position components and another one for the texture coordinates. We need to define another attribute for the texture coordinates, therefore the buffer of `VkVertexInputAttributeDescription` will have an extra element.  The attribute definition itself is quite similar to the one used for the positions, in this case, the size will be for two floats. Finally, the stride need to be update due to the length increase.

## Modifying the render pass

In order to properly render 3D models we need to store depth information. We need to output that depth data while rendering to a image that will hold depth values. This will allow the GPU to perform depth testing operations.  This means, that we need to add another output attachment to the render pass we are using. Therefore, we need to modify the `SwapChainRenderPass` to add another attachment.

```java
public SwapChainRenderPass(SwapChain swapChain, int depthImageFormat) {
    this.swapChain = swapChain;

    try (MemoryStack stack = MemoryStack.stackPush()) {
        VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.callocStack(2, stack);

        // Color attachment
        ...

        // Depth attachment
        attachments.get(1)
                .format(depthImageFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        ...
        VkAttachmentReference depthReference = VkAttachmentReference.mallocStack(stack)
                .attachment(1)
                .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

        VkSubpassDescription.Buffer subPass = VkSubpassDescription.calloc(1)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(colorReference.remaining())
                .pColorAttachments(colorReference)
                .pDepthStencilAttachment(depthReference);
       ...
    }
}
```

The constructor, in addition to the swap chain, receives now the format of the image which will be used to store depth values. We need to add a new `VkAttachmentDescription` to describe the new depth attachment. Some remarks about the parameters:

- The format of that attachment is the format of the `depthImage`.

- We are not using multi-sampling, we just pass the `VK_SAMPLE_COUNT_1_BIT`. 

- The value used for the `loadOp` attribute is the same as in the color attachment. We want their contents to be cleared at the start of this render pass. 

- The vale used for the `storeOp` is different than the one used for the color attachment. In this case, we are not going t o use the contents of the depth buffer for anything after the render pass finishes. In t he case of the color attachment, it need to be preserved to present the contents to the screen. In this case we can use the `VK_ATTACHMENT_STORE_OP_DONT_CARE`  to tell the GPU that they contents may be discarded.

- We want also an automatic transition from the initial layout  `VK_IMAGE_LAYOUT_UNDEFINED`) to the final layout (`VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL`).

Then we create a reference to the depth attachment (`VkAttachmentReference`) and set it as a depth stencil attachment (`pDepthStencilAttachment`) in the `VkSubpassDescription` structure.

## Scene changes

If we are going to represent 3D scenes, we need to project from the 3D world into a 2D space (the screen). We will need to use a perspective projection matrix. We will create a new class that will support its creation and update (due to windows resizing) named `Perspective`:

```java
package org.vulkanb.eng.scene;

import org.joml.Matrix4f;

public class Perspective {

    private static final float FOV = (float) Math.toRadians(60.0f);
    private static final float Z_FAR = 100.f;
    private static final float Z_NEAR = 0.01f;

    private Matrix4f perspectiveMatrix;

    public Perspective() {
        this.perspectiveMatrix = new Matrix4f();
    }

    public Matrix4f getPerspectiveMatrix() {
        return this.perspectiveMatrix;
    }

    public void resize(int width, int height) {
        this.perspectiveMatrix.identity();
        this.perspectiveMatrix.perspective(FOV, (float) width / (float) height, Z_NEAR, Z_FAR, true);
    }
}
```

We are using the [JOML](https://github.com/JOML-CI/JOML) library to create a `Matrix4f`  which will hold the projection matrix. The `resize`should be called, at least once, to initialize the parameters of the matrix with the correct parameters (by invoking the `Matrix4f` `perspective`method).

We are going also to introduce a new concept for the engine that will allow to define game entities and use the same `Mesh` to render multiple elements. Instead of rendering meshes, we will have entities which have some properties, such as their scale, position and rotation and will be associated to a mesh.  They can model a payer. NPCs or scene objects and will be managed by a class named `Entity`, which is defined like this:

```java
package org.vulkanb.eng.scene;

import org.joml.*;

public class Entity {

    private String id;
    private String meshId;
    private Matrix4f modelMatrix;
    private Vector3f position;
    private Quaternionf rotation;
    private float scale;

    public Entity(String id, String meshId, Vector3f position) {
        this.id = id;
        this.meshId = meshId;
        this.position = position;
        this.scale = 1;
        this.rotation = new Quaternionf();
        this.modelMatrix = new Matrix4f();
        updateModelMatrix();
    }

    public String getId() {
        return this.id;
    }

    public String getMeshId() {
        return this.meshId;
    }

    public Matrix4f getModelMatrix() {
        return this.modelMatrix;
    }

    public Vector3f getPosition() {
        return this.position;
    }

    public Quaternionf getRotation() {
        return this.rotation;
    }

    public float getScale() {
        return this.scale;
    }

    public final void setPosition(float x, float y, float z) {
        this.position.x = x;
        this.position.y = y;
        this.position.z = z;
        this.updateModelMatrix();
    }

    public void setScale(float scale) {
        this.scale = scale;
        this.updateModelMatrix();
    }

    public void updateModelMatrix() {
        this.modelMatrix.identity().translationRotateScale(this.position, this.rotation, this.scale);
    }
}
```

Each `Entity` shall have an identifier which should be unique. It is also linked to the mesh that will be used to render it through the `meshId` attribute.  An  `Entity` will have also a position, a rotation (modeled using a  quaternion) and a scale. With all that information we are able to construct a model matrix by calling the `updateModelMatrix` from the `Matrix4f` class.  The `updateModelMatrix` should be called, each time the position, rotation or scale changes.

Now we can setup the required infrastructure to put the `Perspective`and `Entity`classes into work. We will add this to an empty class which has been there since the beginning, the  `Scene` class. The class definition starts like this:

```java
public class Scene {

    private Map<String, List<Entity>> entitiesMap;
    private Perspective perspective;

    public Scene(Window window) {
        this.entitiesMap = new HashMap<>();
        this.perspective = new Perspective();
        this.perspective.resize(window.getWidth(), window.getHeight());
    }
```

The constructor receives the a `Window`instance and creates a `Map`of  `List`s which will contain `Entity` instances. That map will organized the entities by its `meshId`. The constructor initializes that map and also creates an instance of the `Perspective` class, which will hold the perspective matrix. The next method will be used to add new entities:

```java
...
    public void addEntity(Entity entity) {
        List<Entity> entities = this.entitiesMap.get(entity.getMeshId());
        if (entities == null) {
            entities = new ArrayList<>();
            this.entitiesMap.put(entity.getMeshId(), entities);
        }
        entities.add(entity);
    }
...
```

As it can be seen, the entities are organized by their `meshId`. Those entities which share the same `meshId`will be placed inside a `List` associated to that identifier. This will allow us to organized the rendering later by meshes.  Although each entity has different parameters they will share the vertices, textures, etc. defined by the mesh. Organizing the rendering around mesh information will allow us to bind those common resources just once per mesh. The rest of the methods, are used to access the entities map, to get and remove specific entities (suing its identifier) and to get the perspective matrix.

```java
    public List<Entity> getEntitiesByMeshId(String meshId) {
        return this.entitiesMap.get(meshId);
    }

    public Map<String, List<Entity>> getEntitiesMap() {
        return this.entitiesMap;
    }

    public Perspective getPerspective() {
        return perspective;
    }

    public void removeAllEntities() {
        this.entitiesMap.clear();
    }

    public void removeEntity(Entity entity) {
        List<Entity> entities = this.entitiesMap.get(entity.getMeshId());
        if (entities != null) {
            entities.removeIf(e -> e.getId().equals(entity.getId()));
        }
    }
}
```

## Modifying the pipeline

We need also to modify the pipeline to include 
