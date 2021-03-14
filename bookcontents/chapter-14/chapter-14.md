# Animation

In this chapter we will add support for skeletal animations using compte shaders to perform the required transformations to animate a model. By doing so, we will handle static and animated models in the geometry and shadow phases exactly the same way. The compute shader will perform the required transformations and wil dump the results in a buffer. By doing that way, we will not need to change a line of our shaders, we will just be accessing buffers that have vertex information with the same layout. Please keep in mind that, in order to keep this example as simple as possible, we will simplify the animation mechanism, for example, we will not be interpolating between animation key frames and we will not control animation duration.

Also, we will introduce also memory barriers, which is another synchronization mechanism. As you already probably know, Vulkan synchronization is a complex topic. Vulkan specification could be certainly improved in this area. The article that really helped me in trying to understand this important topic is this: http://themaister.net/blog/2019/08/14/yet-another-blog-explaining-vulkan-synchronization/. You should really read it carefully to get a better understanding.

You can find the complete source code for this chapter [here](../../booksamples/chapter-14).

## Skeletal animation introduction

In skeletal animation the way a model is transformed to play an animation is defined by its underlying skeleton. A skeleton is nothing more than a hierarchy of special points called joints. In addition to that, the final position of each joint is affected by the position of their parents. For instance, think of a wrist: the position of a wrist is modified if a character moves the elbow and also if it moves the shoulder.

Joints do not need to represent a physical bone or articulation: they are artifacts that allow the creatives to model an animation  (we may use sometimes the terms bone and joint to refer to the same ting). The models still have vertices that define the different positions, but, in skeletal animation, vertices are drawn based on the position of the joints they are related to and modulated by a set of weights. If we draw a model using just the vertices, without taking into consideration the joints, we would get a 3D model in what is called the bind pose. Each animation is divided into key frames which basically describes the transformations that should be applied to each joint. By changing those transformations, changing those key frames, along time, we are able to animate the model. Those transformations are based on 4x4 matrices which model the displacement and rotation of each joint according to the hierarchy (basically each joint must accumulate the transformations defined by its parents).

If you are reading this, you might probably already know the fundamentals of skeletal animations. The purpose of this chapter is not to explain this in detail but to show an example on how this can be implemented using Vulkan with compute shaders. If you need all the details of skeletal animations you can check this [excellent tutorial](http://ogldev.atspace.co.uk/www/tutorial38/tutorial38.html).

## Loading the models

We need to modify the code that loads 3D models to support animations. The first step is to modify the `ModelData` class to store the data required to animate models:
```java
public class ModelData {
    private List<AnimMeshData> animMeshDataList;
    private List<Animation> animationsList;
    ...
    public List<AnimMeshData> getAnimMeshDataList() {
        return animMeshDataList;
    }

    public List<Animation> getAnimationsList() {
        return animationsList;
    }
    ...
    public void setAnimMeshDataList(List<AnimMeshData> animMeshDataList) {
        this.animMeshDataList = animMeshDataList;
    }

    public void setAnimationsList(List<Animation> animationsList) {
        this.animationsList = animationsList;
    }

    public record AnimMeshData(float[] weights, int[] boneIds) {
    }

    public record AnimatedFrame(Matrix4f[] jointMatrices) {
    }

    public record Animation(String name, double duration, List<AnimatedFrame> frames) {
    }
    ...
}
```
The new `animMeshDataList` attribute is the equivalent of the `meshDataList` one. That list will contain an entry for each mesh storing the relevant data for animated models. In this case, that data is grouped under the `AnimMeshData` and contains two arrays that will contain the weights that will modulate the transformations applied to the joints related to each vertex (related by their identifier in the hierarchy). That data is common to all the animations supported by the model, since it is related to the model structure itself, its skeleton. The `animationsList` attribute holds the list of animations defined for a model. An animation is described by the `Animation` record and consists on a name the duration of the animation and the data of the key frames that compose the animation. Key frame data is defined by the `AnimatedFrame` record which contains the transformation matrices for each of the model joints for that specific frame. Therefore, in order to load animated models we just need to get the additional structural data for mesh (weights and the joints they apply to) and the transformation matrices for each of those joints per animation key frame.

After that we need to modify the `Entity` class to add new attributes to control its animation state to pause / resume the animation, to select the proper animation and to select a specific key frame):
```java
public class Entity {

    private EntityAnimation entityAnimation;
    ...
    public EntityAnimation getEntityAnimation() {
        return entityAnimation;
    }    
    ...
    public void setEntityAnimation(EntityAnimation entityAnimation) {
        this.entityAnimation = entityAnimation;
    }
    ...
    public static class EntityAnimation {
        private int animationIdx;
        private int currentFrame;
        private boolean started;

        public EntityAnimation(boolean started, int animationIdx, int currentFrame) {
            this.started = started;
            this.animationIdx = animationIdx;
            this.currentFrame = currentFrame;
        }

        public int getAnimationIdx() {
            return animationIdx;
        }

        public int getCurrentFrame() {
            return currentFrame;
        }

        public boolean isStarted() {
            return started;
        }

        public void setAnimationIdx(int animationIdx) {
            this.animationIdx = animationIdx;
        }

        public void setCurrentFrame(int currentFrame) {
            this.currentFrame = currentFrame;
        }

        public void setStarted(boolean started) {
            this.started = started;
        }
    }    
}
```

After all those changes we can modify the `ModelLoader` class to load animation data using Assimp. We will examine the changes in a logical order (not the alphabetical order which is used in source files). The starting point will be the `loadModel` methods.
```java
public class ModelLoader {
    ...
    public static ModelData loadModel(String modelId, String modelPath, String texturesDir, boolean animation) {
        return loadModel(modelId, modelPath, texturesDir, aiProcess_GenSmoothNormals | aiProcess_JoinIdenticalVertices |
                aiProcess_Triangulate | aiProcess_FixInfacingNormals | aiProcess_CalcTangentSpace | aiProcess_LimitBoneWeights |
                (animation ? 0 : aiProcess_PreTransformVertices));
    }

    public static ModelData loadModel(String modelId, String modelPath, String texturesDir, int flags) {
        ...
        ModelData modelData = new ModelData(modelId, meshDataList, materialList);

        int numAnimations = aiScene.mNumAnimations();
        if (numAnimations > 0) {
            LOGGER.debug("Processing animations");
            List<Bone> boneList = new ArrayList<>();
            List<ModelData.AnimMeshData> animMeshDataList = new ArrayList<>();
            for (int i = 0; i < numMeshes; i++) {
                AIMesh aiMesh = AIMesh.create(aiMeshes.get(i));
                ModelData.AnimMeshData animMeshData = processBones(aiMesh, boneList);
                animMeshDataList.add(animMeshData);
            }
            modelData.setAnimMeshDataList(animMeshDataList);

            Node rootNode = buildNodesTree(aiScene.mRootNode(), null);
            Matrix4f globalInverseTransformation = toMatrix(aiScene.mRootNode().mTransformation()).invert();
            List<ModelData.Animation> animations = processAnimations(aiScene, boneList, rootNode, globalInverseTransformation);
            modelData.setAnimationsList(animations);
        }

        aiReleaseImport(aiScene);
        LOGGER.debug("Loaded model [{}]", modelPath);
        return modelData;
    }
    ...
}
```
As you can see we are using a new flag: `aiProcess_LimitBoneWeights` that limits the number of bones simultaneously affecting a single vertex to a maximum value (the default maximum values is `4`). The `loadModel` method version that automatically sets the flags receives an extra parameter which indicates if this is an animated model or not. We use that parameter to avoid setting the `aiProcess_PreTransformVertices` for animated models. That flag performs some transformation over the data loaded so the model is placed in the origin and the coordinates are corrected. We cannot use this flag if the model uses animations because it will remove that information. In the `loadModel` method version that actually performs the loading tasks, we have added, at the end, code to load animation data. We first load the skeleton structure (the bones hierarchy) and the weights associated to each vertex. With tha information, we construct `ModelData.AnimMeshData` instances (one per Mesh). After that, we retrieve the different animations and construct the transformation data per key frame.

The `processBones` method is defined like this:
```java
public class ModelLoader {
    ...
    public static final int MAX_WEIGHTS = 4;
    ...
    private static ModelData.AnimMeshData processBones(AIMesh aiMesh, List<Bone> boneList) {
        List<Integer> boneIds = new ArrayList<>();
        List<Float> weights = new ArrayList<>();

        Map<Integer, List<VertexWeight>> weightSet = new HashMap<>();
        int numBones = aiMesh.mNumBones();
        PointerBuffer aiBones = aiMesh.mBones();
        for (int i = 0; i < numBones; i++) {
            AIBone aiBone = AIBone.create(aiBones.get(i));
            int id = boneList.size();
            Bone bone = new Bone(id, aiBone.mName().dataString(), toMatrix(aiBone.mOffsetMatrix()));
            boneList.add(bone);
            int numWeights = aiBone.mNumWeights();
            AIVertexWeight.Buffer aiWeights = aiBone.mWeights();
            for (int j = 0; j < numWeights; j++) {
                AIVertexWeight aiWeight = aiWeights.get(j);
                VertexWeight vw = new VertexWeight(bone.boneId(), aiWeight.mVertexId(),
                        aiWeight.mWeight());
                List<VertexWeight> vertexWeightList = weightSet.get(vw.vertexId());
                if (vertexWeightList == null) {
                    vertexWeightList = new ArrayList<>();
                    weightSet.put(vw.vertexId(), vertexWeightList);
                }
                vertexWeightList.add(vw);
            }
        }

        int numVertices = aiMesh.mNumVertices();
        for (int i = 0; i < numVertices; i++) {
            List<VertexWeight> vertexWeightList = weightSet.get(i);
            int size = vertexWeightList != null ? vertexWeightList.size() : 0;
            for (int j = 0; j < MAX_WEIGHTS; j++) {
                if (j < size) {
                    VertexWeight vw = vertexWeightList.get(j);
                    weights.add(vw.weight());
                    boneIds.add(vw.boneId());
                } else {
                    weights.add(0.0f);
                    boneIds.add(0);
                }
            }
        }

        return new ModelData.AnimMeshData(listFloatToArray(weights), listIntToArray(boneIds));
    }
    ...
}
```
In that `processBones` method, we first construct a list of bones. Each bone will have an identifier which will be used later on to relate them to the wights to be applied for each vertex. That information is stored in the record `ModelData.AnimMeshData`.

The `buildNodesTree` method is quite simple, It just traverses the nodes hierarchy starting from the root node constructing a tree of nodes:
```java
public class ModelLoader {
    ...
    private static Node buildNodesTree(AINode aiNode, Node parentNode) {
        String nodeName = aiNode.mName().dataString();
        Node node = new Node(nodeName, parentNode, toMatrix(aiNode.mTransformation()));

        int numChildren = aiNode.mNumChildren();
        PointerBuffer aiChildren = aiNode.mChildren();
        for (int i = 0; i < numChildren; i++) {
            AINode aiChildNode = AINode.create(aiChildren.get(i));
            Node childNode = buildNodesTree(aiChildNode, node);
            node.addChild(childNode);
        }
        return node;
    }
    ...
}
```

Let’s review the `processAnimations` method, which is defined like this:
```java
public class ModelLoader {
    ...
    public static final int MAX_JOINTS = 150;
    private static final Matrix4f IDENTITY_MATRIX = new Matrix4f();
    ...    
    private static List<ModelData.Animation> processAnimations(AIScene aiScene, List<Bone> boneList,
                                                               Node rootNode, Matrix4f globalInverseTransformation) {
        List<ModelData.Animation> animations = new ArrayList<>();

        // Process all animations
        int numAnimations = aiScene.mNumAnimations();
        PointerBuffer aiAnimations = aiScene.mAnimations();
        for (int i = 0; i < numAnimations; i++) {
            AIAnimation aiAnimation = AIAnimation.create(aiAnimations.get(i));
            int maxFrames = calcAnimationMaxFrames(aiAnimation);

            List<ModelData.AnimatedFrame> frames = new ArrayList<>();
            ModelData.Animation animation = new ModelData.Animation(aiAnimation.mName().dataString(), aiAnimation.mDuration(), frames);
            animations.add(animation);

            for (int j = 0; j < maxFrames; j++) {
                Matrix4f[] jointMatrices = new Matrix4f[MAX_JOINTS];
                Arrays.fill(jointMatrices, IDENTITY_MATRIX);
                ModelData.AnimatedFrame animatedFrame = new ModelData.AnimatedFrame(jointMatrices);
                buildFrameMatrices(aiAnimation, boneList, animatedFrame, j, rootNode,
                        rootNode.getNodeTransformation(), globalInverseTransformation);
                frames.add(animatedFrame);
            }
        }
        return animations;
    }
    ...
}
```

This method returns a `List` of `ModelData.Animation` instances (Remember that a model can have more than one animation). For each of those animations we construct a list of animation frames (`ModelData.AnimatedFrame` instances), which contain essentially a list of the transformation matrices to be applied to each of the bones that compose the model. For each of the animations, we calculate the maximum number of frames by calling the method `calcAnimationMaxFrames`, which is defined like this: 
```java
public class ModelLoader {
    ...
    private static int calcAnimationMaxFrames(AIAnimation aiAnimation) {
        int maxFrames = 0;
        int numNodeAnims = aiAnimation.mNumChannels();
        PointerBuffer aiChannels = aiAnimation.mChannels();
        for (int i = 0; i < numNodeAnims; i++) {
            AINodeAnim aiNodeAnim = AINodeAnim.create(aiChannels.get(i));
            int numFrames = Math.max(Math.max(aiNodeAnim.mNumPositionKeys(), aiNodeAnim.mNumScalingKeys()),
                    aiNodeAnim.mNumRotationKeys());
            maxFrames = Math.max(maxFrames, numFrames);
        }

        return maxFrames;
    }
    ...
}
```

Each `AINodeAnim` instance defines some transformations to be applied to a node in the model for a specific frame. These transformations, for a specific node, are defined in the `AINodeAnim` instance. These transformations are defined in the form of position translations, rotations and scaling values. The trick here is that, for example, for a specific node, translation values can stop at a specific frae, but rotations and scaling values can continue for the next frames. In this case, we will have less translation values than rotation or scaling ones. Therefore, a good approximation, to calculate the maximum number of frames is to use the maximum value. The problem gest more complex, because this is defines per node. A node can define just some transformations for the first frames and do not apply more modifications for the rest. In this case, we should use always the last defined values. Therefore, we get the maximum number for all the animations associated to the nodes.

Going back to the `processAnimations` method, with that information, we are ready to iterate over the different frames and build the transformation matrices for the bones by calling the `buildFrameMatrices` method. For each frame we start with the root node, and will apply the transformations recursively from top to down of the nodes hierarchy. The `buildFrameMatrices` is defined like this:
```java
public class ModelLoader {
    ...
    private static void buildFrameMatrices(AIAnimation aiAnimation, List<Bone> boneList, ModelData.AnimatedFrame animatedFrame,
                                           int frame, Node node, Matrix4f parentTransformation, Matrix4f globalInverseTransform) {
        String nodeName = node.getName();
        AINodeAnim aiNodeAnim = findAIAnimNode(aiAnimation, nodeName);
        Matrix4f nodeTransform = node.getNodeTransformation();
        if (aiNodeAnim != null) {
            nodeTransform = buildNodeTransformationMatrix(aiNodeAnim, frame);
        }
        Matrix4f nodeGlobalTransform = new Matrix4f(parentTransformation).mul(nodeTransform);

        List<Bone> affectedBones = boneList.stream().filter(b -> b.boneName().equals(nodeName)).collect(Collectors.toList());
        for (Bone bone : affectedBones) {
            Matrix4f boneTransform = new Matrix4f(globalInverseTransform).mul(nodeGlobalTransform).
                    mul(bone.offsetMatrix());
            animatedFrame.jointMatrices()[bone.boneId()] = boneTransform;
        }

        for (Node childNode : node.getChildren()) {
            buildFrameMatrices(aiAnimation, boneList, animatedFrame, frame, childNode, nodeGlobalTransform,
                    globalInverseTransform);
        }
    }
    ...
}
```

We get the transformation associated to the node. Then we check if this node has an animation node associated to it. If so, we need to get the proper translation, rotation and scaling transformations that apply to the frame that we are handling. With that information, we get the bones associated to that node and update the transformation matrix for each of those bones, for that specific frame by multiplying:

* The model inverse global transformation matrix (the inverse of the root node transformation matrix).
* The transformation matrix for the node.
* The bone offset matrix.

After that, we iterate over the children nodes, using the node transformation matrix as the parent matrix for those child nodes.

Let’s review the `buildNodeTransformationMatrix` method:
```java
public class ModelLoader {
    ...
    private static Matrix4f buildNodeTransformationMatrix(AINodeAnim aiNodeAnim, int frame) {
        AIVectorKey.Buffer positionKeys = aiNodeAnim.mPositionKeys();
        AIVectorKey.Buffer scalingKeys = aiNodeAnim.mScalingKeys();
        AIQuatKey.Buffer rotationKeys = aiNodeAnim.mRotationKeys();

        AIVectorKey aiVecKey;
        AIVector3D vec;

        Matrix4f nodeTransform = new Matrix4f();
        int numPositions = aiNodeAnim.mNumPositionKeys();
        if (numPositions > 0) {
            aiVecKey = positionKeys.get(Math.min(numPositions - 1, frame));
            vec = aiVecKey.mValue();
            nodeTransform.translate(vec.x(), vec.y(), vec.z());
        }
        int numRotations = aiNodeAnim.mNumRotationKeys();
        if (numRotations > 0) {
            AIQuatKey quatKey = rotationKeys.get(Math.min(numRotations - 1, frame));
            AIQuaternion aiQuat = quatKey.mValue();
            Quaternionf quat = new Quaternionf(aiQuat.x(), aiQuat.y(), aiQuat.z(), aiQuat.w());
            nodeTransform.rotate(quat);
        }
        int numScalingKeys = aiNodeAnim.mNumScalingKeys();
        if (numScalingKeys > 0) {
            aiVecKey = scalingKeys.get(Math.min(numScalingKeys - 1, frame));
            vec = aiVecKey.mValue();
            nodeTransform.scale(vec.x(), vec.y(), vec.z());
        }

        return nodeTransform;
    }
    ...
}
```

The `AINodeAnim` instance defines a set of keys that contain translation, rotation and scaling information. These keys are referred to specific instant of times. We assume that information is ordered in time, and construct a list of matrices that contain the transformation to be applied for each frame.

Finally, the `toMatrix` just copies an Assimp matrix to a JOML Matrix:
```java
public class ModelLoader {
    ...
    private static Matrix4f toMatrix(AIMatrix4x4 aiMatrix4x4) {
        Matrix4f result = new Matrix4f();
        result.m00(aiMatrix4x4.a1());
        result.m10(aiMatrix4x4.a2());
        result.m20(aiMatrix4x4.a3());
        result.m30(aiMatrix4x4.a4());
        result.m01(aiMatrix4x4.b1());
        result.m11(aiMatrix4x4.b2());
        result.m21(aiMatrix4x4.b3());
        result.m31(aiMatrix4x4.b4());
        result.m02(aiMatrix4x4.c1());
        result.m12(aiMatrix4x4.c2());
        result.m22(aiMatrix4x4.c3());
        result.m32(aiMatrix4x4.c4());
        result.m03(aiMatrix4x4.d1());
        result.m13(aiMatrix4x4.d2());
        result.m23(aiMatrix4x4.d3());
        result.m33(aiMatrix4x4.d4());

        return result;
    }
    ...
}
```

All that new information needs to be handled in the `VulkanModel` class so it is loaded into the GPU. This class now defines the following new attribute:
```java
public class VulkanModel {

    private List<VulkanModel.VulkanAnimation> animationList;
    ...
    public record VulkanAnimation(String name, List<VulkanBuffer> frameBufferList) {
        public void cleanup() {
            frameBufferList.forEach(VulkanBuffer::cleanup);
        }
    }
    ...
    public record VulkanMesh(VulkanBuffer verticesBuffer, VulkanBuffer indicesBuffer, int numIndices,
                             VulkanBuffer weightsBuffer) {
        public void cleanup() {
            verticesBuffer.cleanup();
            indicesBuffer.cleanup();
            if (weightsBuffer != null) {
                weightsBuffer.cleanup();
            }
        }
    }    
}
```

The `animationList` attribute holds a list which will contain one entry per animation. Each entry, modelled by the `VulkanAnimation` record, is defined by a name and a list of buffers. Those buffers will contain the joint transformations buffers for each of the animation frames. The `VulkanMesh` record needs also to be modified. Besides storing the vertex and indices buffer, we need to define a new buffer that store the weights and the joints identifiers that will be associated to each vertex (`weightsBuffer`).  

Now that we have all the new structures, we can view the changes on the `transformModels` method:
```java
public class VulkanModel {
    ...
        for (ModelData modelData : modelDataList) {
            ...
            List<ModelData.Animation> animationsList = modelData.getAnimationsList();
            boolean hasAnimation = animationsList != null && !animationsList.isEmpty();
            if (hasAnimation) {
                vulkanModel.animationList = new ArrayList<>();
                for (ModelData.Animation animation : animationsList) {
                    List<VulkanBuffer> vulkanFrameBufferList = new ArrayList<>();
                    VulkanAnimation vulkanAnimation = new VulkanAnimation(animation.name(), vulkanFrameBufferList);
                    vulkanModel.animationList.add(vulkanAnimation);
                    List<ModelData.AnimatedFrame> frameList = animation.frames();
                    for (ModelData.AnimatedFrame frame : frameList) {
                        TransferBuffers jointMatricesBuffers = createJointMatricesBuffers(device, frame);
                        stagingBufferList.add(jointMatricesBuffers.srcBuffer());
                        recordTransferCommand(cmd, jointMatricesBuffers);
                        vulkanFrameBufferList.add(jointMatricesBuffers.dstBuffer);
                    }
                }
            }

            // Transform meshes loading their data into GPU buffers
            int meshCount = 0;
            for (ModelData.MeshData meshData : modelData.getMeshDataList()) {
                ...
                TransferBuffers weightsBuffers = null;
                List<ModelData.AnimMeshData> animMeshDataList = modelData.getAnimMeshDataList();
                if (animMeshDataList != null && !animMeshDataList.isEmpty()) {
                    weightsBuffers = createWeightsBuffers(device, animMeshDataList.get(meshCount));
                    stagingBufferList.add(weightsBuffers.srcBuffer());
                    recordTransferCommand(cmd, weightsBuffers);
                }

                VulkanModel.VulkanMesh vulkanMesh = new VulkanModel.VulkanMesh(verticesBuffers.dstBuffer(),
                        indicesBuffers.dstBuffer(), meshData.indices().length,
                        weightsBuffers != null ? weightsBuffers.dstBuffer() : null);
                ...

                meshCount++;
            }
            ...
        }
    ...
}
```
If the model has animations, we create the buffers that will hold the join transformation matrices for each of the frames. As usual, we use staging buffers with temporary CPU accessible buffers that get copied to a GPU only one, so we need to record those transitions. We also create the wights buffer for each of the meshes and store that in the `VulkanModel.VulkanMesh` instance. The `createJointMatricesBuffers` is defined like this:
```java
public class VulkanModel {
    ...
    private static TransferBuffers createJointMatricesBuffers(Device device, ModelData.AnimatedFrame frame) {
        Matrix4f[] matrices = frame.jointMatrices();
        int numMatrices = matrices.length;
        int bufferSize = numMatrices * GraphConstants.MAT4X4_SIZE;
        VulkanBuffer srcBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VulkanBuffer dstBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);

        long mappedMemory = srcBuffer.map();
        ByteBuffer matrixBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) srcBuffer.getRequestedSize());
        for (int i = 0; i < numMatrices; i++) {
            matrices[i].get(i * GraphConstants.MAT4X4_SIZE, matrixBuffer);
        }
        srcBuffer.unMap();

        return new TransferBuffers(srcBuffer, dstBuffer);
    }
    ...
}
```

And the `createWeightsBuffers` is defined like this:
```java
public class VulkanModel {
    ...
    private static TransferBuffers createWeightsBuffers(Device device, ModelData.AnimMeshData animMeshData) {
        float[] weights = animMeshData.weights();
        int[] boneIds = animMeshData.boneIds();
        int bufferSize = weights.length * GraphConstants.FLOAT_LENGTH + boneIds.length * GraphConstants.INT_LENGTH;

        VulkanBuffer srcBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VulkanBuffer dstBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);

        long mappedMemory = srcBuffer.map();
        FloatBuffer data = MemoryUtil.memFloatBuffer(mappedMemory, (int) srcBuffer.getRequestedSize());

        int rows = weights.length / 4;
        for (int row = 0; row < rows; row++) {
            int startPos = row * 4;
            data.put(weights[startPos]);
            data.put(weights[startPos + 1]);
            data.put(weights[startPos + 2]);
            data.put(weights[startPos + 3]);
            data.put(boneIds[startPos]);
            data.put(boneIds[startPos + 1]);
            data.put(boneIds[startPos + 2]);
            data.put(boneIds[startPos + 3]);
        }

        srcBuffer.unMap();

        return new TransferBuffers(srcBuffer, dstBuffer);
    }
    ...
}
```

Finally, we need to modify the `cleanup` method and add new ones to check if the model has animations and to retrieve the buffers for each of the animations:
```java
public class VulkanModel {
    ...
    public void cleanup() {
        vulkanMaterialList.forEach(m -> m.vulkanMeshList.forEach((VulkanMesh::cleanup)));
        if (animationList != null) {
            animationList.forEach(VulkanAnimation::cleanup);
        }
    }

    public List<VulkanModel.VulkanAnimation> getAnimationList() {
        return animationList;
    }
    ...
    public boolean hasAnimations() {
        return animationList != null && !animationList.isEmpty();
    }
    ...
}
```

## Compute Shader

Prior to jumping to the code, it is necessary to briefly describe compute shaders. Compute shaders are a bit different than vertex or fragment shaders. Vertex and fragment shaders have a well defined inputs and outputs. For example, when we create a graphics pipeline, we define the structure of the input data for the vertex shader vertex shader. In addition to that, vertex shaders get invoked, "automatically", as many times as required to consume that input. In our examples, up to this point, vertex input contains vertex position, texture coordinates and the normals data. Compute shaders operate differently, they work over buffers as a whole. It is up to us to decide how it will execute and how they will operate over the data they will require to perform their computation and where the results should be stored. Compute shaders access data (for reading and writing) through storage buffers. In our case, we will store binding pose information as read only storage buffers and will store the transformed positions in a read / write storage buffer. That output buffer will later be read in the geometry phase as a regular vertex buffer.

As mentioned above, a key topic of compute shaders is how many times they should be invoked and how the work load is distributed. Compute shaders define the concept of work groups, which are a collection of of shader invocations that can be executed, potentially, in parallel. Work groups are three dimensional, so they will be defined by the triplet `(Wx, Wy, Wz)`, where each of those components must be equal to or greater than `1`.  A compute shader will execute in total `Wx*Wy*Wz` work groups. Work groups have also a size, named local size. Therefore, we can define local size as another triplet `(Lx, Ly, Lz)`. The total number of times a compute shader will be invoked will be the product `Wx*Lx*Wy*Ly*Wz*Lz`. The reason behind specifying these using three dimension parameters is because some data is handled in a more convenient way using 2D or 3D dimensions. You can think for example in a image transformation computation, we would be probably using the data of an image pixel and their neighbor pixels. We could organize the work using 2D computation parameters. In addition to that, work done inside a work group, can share same variables and resources, which may be required when processing 2D or 3D data. Inside the computer shader we will have access to pre-built variables that will identify the invocation we are in so we can properly access the data slice that we want to work with according to our needs.  

In order to support the execution of commands that will go through the compute pipeline, we need first to define a new class named `ComputePipeline` to support the creation of that type of pipelines. Compute pipelines are much simpler than graphics pipelines. Graphics pipelines have a set of fixed and programable stages while the compute pipeline has a single programmable compute shader stage. So let's go with it:
```java
public class ComputePipeline {

    private static final Logger LOGGER = LogManager.getLogger();
    private Device device;
    private long vkPipeline;
    private long vkPipelineLayout;

    public ComputePipeline(PipelineCache pipelineCache, ComputePipeline.PipeLineCreationInfo pipeLineCreationInfo) {
        ...
    }
    ...
}
```

The constructor receives a reference to the pipeline cache and to the `ComputePipeline.PipeLineCreationInfo` class which contains the required information to create the pipeline. The `ComputePipeline.PipeLineCreationInfo` is an inner class, defined as a record, of the `ComputePipeline` class which contains references to the shader program used for this compute pipeline and the layouts of the descriptor used in that shader.

```java
public class ComputePipeline {
    ...
    public record PipeLineCreationInfo(ShaderProgram shaderProgram, DescriptorSetLayout[] descriptorSetLayouts) {
    }
    ...
}
```

Going back to the `ComputePipeline` constructor, we first initialize the `VkPipelineShaderStageCreateInfo` structure with the compute shader information. In this specific case, we receive a `ShaderProgram` instance through the `ComputePipeline.PipeLineCreationInfo` record. The `ShaderProgram` class is also used for the graphics pipeline creation holding shader modules that refer to vertex and fragment shaders. In our case, we just need a compute shader, so we just need a shader module. We will assume that in the first position of the shader modules we will receive that reference.
```java
public class ComputePipeline {
    ...
    public ComputePipeline(PipelineCache pipelineCache, ComputePipeline.PipeLineCreationInfo pipeLineCreationInfo) {
        LOGGER.debug("Creating compute pipeline");
        device = pipelineCache.getDevice();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer lp = stack.callocLong(1);
            ByteBuffer main = stack.UTF8("main");

            ShaderProgram.ShaderModule[] shaderModules = pipeLineCreationInfo.shaderProgram.getShaderModules();
            int numModules = shaderModules != null ? shaderModules.length : 0;
            if (numModules != 1) {
                throw new RuntimeException("Compute pipelines can have only one shader");
            }
            ShaderProgram.ShaderModule shaderModule = shaderModules[0];
            VkPipelineShaderStageCreateInfo shaderStage = VkPipelineShaderStageCreateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(shaderModule.shaderStage())
                    .module(shaderModule.handle())
                    .pName(main);
            ...
        }
        ...
    }
    ...
}
```

After that, we use the descriptors layout information stored in the `ComputePipeline.PipeLineCreationInfo` record to properly setup the `VkPipelineLayoutCreateInfo` structure, which allows us to create the pipeline layout.
```java
public class ComputePipeline {
    ...
    public ComputePipeline(PipelineCache pipelineCache, ComputePipeline.PipeLineCreationInfo pipeLineCreationInfo) {
        ...
            DescriptorSetLayout[] descriptorSetLayouts = pipeLineCreationInfo.descriptorSetLayouts();
            int numLayouts = descriptorSetLayouts.length;
            LongBuffer ppLayout = stack.mallocLong(numLayouts);
            for (int i = 0; i < numLayouts; i++) {
                ppLayout.put(i, descriptorSetLayouts[i].getVkDescriptorLayout());
            }
            VkPipelineLayoutCreateInfo pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(ppLayout);
            vkCheck(vkCreatePipelineLayout(device.getVkDevice(), pPipelineLayoutCreateInfo, null, lp),
                    "Failed to create pipeline layout");
            vkPipelineLayout = lp.get(0);
        ...
    }
    ...
}
```

Finally, we can create the pipeline itself:
```java
public class ComputePipeline {
    ...
    public ComputePipeline(PipelineCache pipelineCache, ComputePipeline.PipeLineCreationInfo pipeLineCreationInfo) {
        ...
            VkComputePipelineCreateInfo.Buffer computePipelineCreateInfo = VkComputePipelineCreateInfo.callocStack(1, stack)
                    .sType(VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO)
                    .stage(shaderStage)
                    .layout(vkPipelineLayout);
            vkCheck(vkCreateComputePipelines(device.getVkDevice(), pipelineCache.getVkPipelineCache(), computePipelineCreateInfo,
                    null, lp), "Error creating compute pipeline");
            vkPipeline = lp.get(0);
        }
    }
    ...
}        
``` 

The `ComputePipeline` class is completed by the `getter` methods to retrieve the pipeline and pipeline layout handles and a `cleanup` method to release the resources.
```java
public class ComputePipeline {
    ...
    public void cleanup() {
        LOGGER.debug("Destroying compute pipeline");
        vkDestroyPipelineLayout(device.getVkDevice(), vkPipelineLayout, null);
        vkDestroyPipeline(device.getVkDevice(), vkPipeline, null);
    }

    public long getVkPipeline() {
        return vkPipeline;
    }

    public long getVkPipelineLayout() {
        return vkPipelineLayout;
    }
    ...
}
```

In the compute shader, we will be accessing buffers, which will hold the bind pose for the animated models. The way we access those buffers directly is through buffer storage descriptor sets. These descriptor sets are linked directly to a buffer and allow read and write operations. Therefore, we need to define a new descriptor set layout type for them:
```java
public abstract class DescriptorSetLayout {
    ...
    public static class StorageDescriptorSetLayout extends SimpleDescriptorSetLayout {
        public StorageDescriptorSetLayout(Device device, int binding, int stage) {
            super(device, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, binding, stage);
        }
    }
    ...
}
```

After that, we can define a new class that will be used to instantiate the storage buffer descriptor sets. As in previous cases, we just need to define a new class that inherit from `SimpleDescriptorSet` as an inner class of the `DescriptorSet` class, which basically sets is type to the `VK_DESCRIPTOR_TYPE_STORAGE_BUFFER` value:
```java
public abstract class DescriptorSet {
    ...
    public static class StorageDescriptorSet extends SimpleDescriptorSet {

        public StorageDescriptorSet(DescriptorPool descriptorPool, DescriptorSetLayout descriptorSetLayout,
                                    VulkanBuffer buffer, int binding) {
            super(descriptorPool, descriptorSetLayout, buffer, binding, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                    buffer.getRequestedSize());
        }
    }
    ...
}
```

The next step is to update the `Queue` class to support the creation of queues that belong to a family that allow the submission of compute commands. In order to do that, we will create a new inner class named `ComputeQueue`, that will create compute queues, and which is defined like this:
```java
public class Queue {
    ...
    public static class ComputeQueue extends Queue {

        public ComputeQueue(Device device, int queueIndex) {
            super(device, getComputeQueueFamilyIndex(device), queueIndex);
        }

        private static int getComputeQueueFamilyIndex(Device device) {
            int index = -1;
            PhysicalDevice physicalDevice = device.getPhysicalDevice();
            VkQueueFamilyProperties.Buffer queuePropsBuff = physicalDevice.getVkQueueFamilyProps();
            int numQueuesFamilies = queuePropsBuff.capacity();
            for (int i = 0; i < numQueuesFamilies; i++) {
                VkQueueFamilyProperties props = queuePropsBuff.get(i);
                boolean computeQueue = (props.queueFlags() & VK_QUEUE_COMPUTE_BIT) != 0;
                if (computeQueue) {
                    index = i;
                    break;
                }
            }

            if (index < 0) {
                throw new RuntimeException("Failed to get compute Queue family index");
            }
            return index;
        }
    }
    ...
}
```

We need to synchronize the computer shader phase with the geometry phase. In the compute phase, we update the buffers that will be rendered in the second phase, so we need to avoid them to overlap. One important issue to consider is that the queue used to submit compute dispatch commands might be different from the queue used for graphics commands. Therefore, we cannot simply use execution barriers for synchronization. Execution barriers can only be used to perform in-queue synchronization. In addition to that, we need also to ensure that the memory written during the compute phase is visible in the vertex shader at the geometry phase. In order to achieve that, we will use a global memory barrier.

Barriers are a way to split commands execution into two parts, the first part controls what is needed to be executed before the barrier and the second one what gets executed after the barrier. Barriers are submitted using the `vkCmdPipelineBarrier` function, which can be used to submit pipeline barriers, global memory barriers, buffer barriers or image barriers. In our case, we will be using global memory barriers. 

The function ` vkCmdPipelineBarrier` function requires to specify, essentially, two parameters:
- `srcStageMask`: This refers to the pipeline stage that we are waiting to complete.
- `dstStageMask`: This refers to the pipeline stage which should not start after all the work affected by the conditions specified for the first part of the barrier is completed.

Global memory barriers are defined by two parameters, `srcAccessMask` and `dstAccessMask`, which in combination with the parameters described above, provoke the following to be executed in order:
- All the commands submitted prior to the barrier must complete the stage specified by `srcStageMask`.
- All memory writes performed in combination of `srcStageMask` and `srcAccessMask` must be available (the data is written into the memory).
- The memory is visible (the caches are invalidated so they can pull the modified data) to any combination of ` dstStageMask` and ` dstAccessMask`.
- All the commands submitted after the barrier, which were blocked in the `dstStageMask` can now execute.

We will create a new class, named `MemoryBarrier`, to model global memory barriers. It is a simple class, that just allocates the structure required to submit a barrier (`VkMemoryBarrier`):
```java
package org.vulkanb.eng.graph.vk;

import org.lwjgl.vulkan.VkMemoryBarrier;

import static org.lwjgl.vulkan.VK11.VK_STRUCTURE_TYPE_MEMORY_BARRIER;

public class MemoryBarrier {

    private VkMemoryBarrier.Buffer vkMemoryBarrier;

    public MemoryBarrier(int srcAccessMask, int dstAccessMask) {
        vkMemoryBarrier = VkMemoryBarrier.calloc(1)
                .sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER)
                .srcAccessMask(srcAccessMask)
                .dstAccessMask(dstAccessMask);
    }

    public VkMemoryBarrier.Buffer getVkMemoryBarrier() {
        return vkMemoryBarrier;
    }
}
```

We have now all the pieces required to perform the animation using a compute shader, so we will create a new class, named `AnimationComputeActivity` to put them into play. The structure of this class is similar to the equivalent classes for the geometry or lighting phases. In the constructor a reference to a compute queue is retrieved, the descriptor sets are created, the compute shader is compiled and a memory barrier that will be used to perform the synchronization is created.
```java
public class AnimationComputeActivity {

    private static final String ANIM_COMPUTE_SHADER_FILE_GLSL = "resources/shaders/animations_comp.glsl";
    private static final String ANIM_COMPUTE_SHADER_FILE_SPV = ANIM_COMPUTE_SHADER_FILE_GLSL + ".spv";
    private static final int LOCAL_SIZE_X = 32;

    private MemoryBarrier memoryBarrier;
    private CommandBuffer commandBuffer;
    private ComputePipeline computePipeline;
    private Queue.ComputeQueue computeQueue;
    private DescriptorPool descriptorPool;
    private DescriptorSetLayout[] descriptorSetLayouts;
    private Device device;
    // Key is the entity id
    private Map<String, List<EntityAnimationBuffer>> entityAnimationsBuffers;
    private Fence fence;
    // Key is the model id
    private Map<String, ModelDescriptorSets> modelDescriptorSetsMap;
    private Scene scene;
    private ShaderProgram shaderProgram;
    private DescriptorSetLayout.StorageDescriptorSetLayout storageDescriptorSetLayout;
    private DescriptorSetLayout.UniformDescriptorSetLayout uniformDescriptorSetLayout;

    public AnimationComputeActivity(CommandPool commandPool, PipelineCache pipelineCache, Scene scene) {
        this.scene = scene;
        device = pipelineCache.getDevice();
        computeQueue = new Queue.ComputeQueue(device, 0);
        createDescriptorPool();
        createDescriptorSets();
        createShaders();
        createPipeline(pipelineCache);
        createCommandBuffers(commandPool);
        modelDescriptorSetsMap = new HashMap<>();
        entityAnimationsBuffers = new HashMap<>();
        memoryBarrier = new MemoryBarrier(0, VK_ACCESS_SHADER_WRITE_BIT);
    }
    ...
}
```

In the `cleanup` method, as usual, we just free the resources:
```java
public class AnimationComputeActivity {
    ...
    public void cleanup() {
        computePipeline.cleanup();
        shaderProgram.cleanup();
        commandBuffer.cleanup();
        descriptorPool.cleanup();
        storageDescriptorSetLayout.cleanup();
        uniformDescriptorSetLayout.cleanup();
        fence.cleanup();
        for (Map.Entry<String, List<EntityAnimationBuffer>> entry : entityAnimationsBuffers.entrySet()) {
            entry.getValue().forEach(EntityAnimationBuffer::cleanup);
        }
    }
    ...
}
```

Now we will review the `createXX` methods called in the constructor. The first one is the `createCommandBuffers`, which creates a command buffer that will be used to record the compute dispatch commands. It also creates a fence to prevent reusing the command buffer while in use. The `createDescriptorPool` method creates a descriptor pool, defining the maximum number of each descriptor type that we are going to use. In our case, we will be using storage descriptor sets for the data that we will use for the animation and uniform buffers that we will use to pass the joint transformation matrices. The `createDescriptorSets` method just creates the layouts of the descriptor sets that we will use in the compute shader. The `createPipeline` method just creates our compute pipeline with the descriptor sets layouts information created previously. Finally, the `createShaders` just creates a shader program, which will contain a shader module which holds the compute shader code. As you can see, they are similar as the ones used in geometry, shadow and lighting phases in previous chapters.
```java
public class AnimationComputeActivity {
    ...
    private void createCommandBuffers(CommandPool commandPool) {
        commandBuffer = new CommandBuffer(commandPool, true, false);
        fence = new Fence(device, true);
    }

    private void createDescriptorPool() {
        EngineProperties engineProperties = EngineProperties.getInstance();
        int maxStorageBuffers = engineProperties.getMaxStorageBuffers();
        int maxJointsMatricesLists = engineProperties.getMaxJointsMatricesLists();
        List<DescriptorPool.DescriptorTypeCount> descriptorTypeCounts = new ArrayList<>();
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(maxStorageBuffers, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER));
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(maxJointsMatricesLists, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER));
        descriptorPool = new DescriptorPool(device, descriptorTypeCounts);
    }

    private void createDescriptorSets() {
        storageDescriptorSetLayout = new DescriptorSetLayout.StorageDescriptorSetLayout(device, 0, VK_SHADER_STAGE_COMPUTE_BIT);
        uniformDescriptorSetLayout = new DescriptorSetLayout.UniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_COMPUTE_BIT);
        descriptorSetLayouts = new DescriptorSetLayout[]{
                storageDescriptorSetLayout,
                storageDescriptorSetLayout,
                storageDescriptorSetLayout,
                uniformDescriptorSetLayout,
        };
    }

    private void createPipeline(PipelineCache pipelineCache) {
        ComputePipeline.PipeLineCreationInfo pipeLineCreationInfo = new ComputePipeline.PipeLineCreationInfo(shaderProgram,
                descriptorSetLayouts);
        computePipeline = new ComputePipeline(pipelineCache, pipeLineCreationInfo);
    }

    private void createShaders() {
        EngineProperties engineProperties = EngineProperties.getInstance();
        if (engineProperties.isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(ANIM_COMPUTE_SHADER_FILE_GLSL, Shaderc.shaderc_compute_shader);
        }
        shaderProgram = new ShaderProgram(device, new ShaderProgram.ShaderModuleData[]
                {
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_COMPUTE_BIT, ANIM_COMPUTE_SHADER_FILE_SPV),
                });
    }
    ...
}
```

We needed to add new configuration properties in the `EngineProperties` class to properly establish the size of the descriptors set pool in the `AnimationComputeActivity` class, here are the changes:
```java
public class EngineProperties {
    ...
    private static final int DEFAULT_MAX_JOINTS_MATRICES_LISTS = 100;
    private static final int DEFAULT_STORAGES_BUFFERS = 100;
    ...
    private int maxJointsMatricesLists;
    private int maxStorageBuffers;
    ...
    private EngineProperties() {
        ...
            maxStorageBuffers = Integer.parseInt(props.getOrDefault("maxStorageBuffers", DEFAULT_STORAGES_BUFFERS).toString());
            maxJointsMatricesLists = Integer.parseInt(props.getOrDefault("maxJointsMatricesLists", DEFAULT_MAX_JOINTS_MATRICES_LISTS).toString());
        ...
    }
    ...
    public int getMaxJointsMatricesLists() {
        return maxJointsMatricesLists;
    }

    public int getMaxStorageBuffers() {
        return maxStorageBuffers;
    }
    ...
}
```

We now will create a new method method that should be called when new models that contain animations are created. That method, named `registerModels`, is defined like this:
```java
public class AnimationComputeActivity {
    ...
    public void registerModels(List<VulkanModel> vulkanModels) {
        for (VulkanModel vulkanModel : vulkanModels) {
            if (!vulkanModel.hasAnimations()) {
                continue;
            }
            String modelId = vulkanModel.getModelId();
            List<List<DescriptorSet>> jointMatricesBufferDescriptorSets = new ArrayList<>();
            for (VulkanModel.VulkanAnimation animation : vulkanModel.getAnimationList()) {
                List<DescriptorSet> animationFrames = new ArrayList<>();
                for (VulkanBuffer jointsMatricesBuffer : animation.frameBufferList()) {
                    animationFrames.add(new DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout,
                            jointsMatricesBuffer, 0));
                }
                jointMatricesBufferDescriptorSets.add(animationFrames);
            }

            List<MeshDescriptorSets> meshDescriptorSetsList = new ArrayList<>();
            for (VulkanModel.VulkanMaterial material : vulkanModel.getVulkanMaterialList()) {
                for (VulkanModel.VulkanMesh mesh : material.vulkanMeshList()) {
                    int vertexSize = 14 * 4;
                    int groupSize = (int) Math.ceil((mesh.verticesBuffer().getRequestedSize() / vertexSize) / (float) LOCAL_SIZE_X);
                    MeshDescriptorSets meshDescriptorSets = new MeshDescriptorSets(
                            new DescriptorSet.StorageDescriptorSet(descriptorPool, storageDescriptorSetLayout, mesh.verticesBuffer(), 0),
                            groupSize,
                            new DescriptorSet.StorageDescriptorSet(descriptorPool, storageDescriptorSetLayout, mesh.weightsBuffer(), 0)
                    );
                    meshDescriptorSetsList.add(meshDescriptorSets);
                }
            }

            ModelDescriptorSets modelDescriptorSets = new ModelDescriptorSets(meshDescriptorSetsList, jointMatricesBufferDescriptorSets);
            modelDescriptorSetsMap.put(modelId, modelDescriptorSets);
        }
    }
    ...
}
```
In this methods, we first discard the models that do not contain animations. For each of the models that contain animations, we create a descriptor set that will hold an array of matrices with the transformation matrices associated to the joints of the model. Those matrices change for each animation frame, so for a model, we will have as many arrays (ans therefore as many descriptors) as animation frames the model has. We will pass that data to the compute shader as uniforms so we use a `UniformDescriptorSet` per frame that will contain that array of matrices. For each mesh of the model we will need at least, two storage buffers, the first one will hold the data for the bind position (position, texture coordinates, normal, tangent and bitangent). That data is composed by 14 floats (4 bytes each) and will be transformed according to the weights and joint matrices to generate the animation. The second storage buffer will contain the weights associated to each vertex (a vertex will have 4 weights that will modulate the bind position using the joint transformation matrices. Each opf those weights will be associated to a joint index). Therefore we need to create two storage descriptor sets per mesh. We combine that information in the `MeshDescriptorSets` record. That record also defines a paramater named `groupSize`, let's explain now what is this parameter for. As mentioned previously, compute shaders invocations are organized in work groups (`Wx`, `Wy` and `Wz`) which have a local size (`Lx`, `Ly` and `Lz`). In our specific case, we will be organizing the work using just one dimension, so the `Wy`, `Wz`, `Ly` and `Lz` values will be set to `1`. The local size is defined in the shader code, and, as we will see later on, we will use a value of `32` for `Lx`. Therefore, the number of times the compute shader will be executed will be equal to `Wx*Lx`. Because of that, we need to divide the total number of vertices, for a mesh, per the local size value (`32`) in order to properly set up the `Wx` value, which is what defines the `groupSize` parameter. Finally, we store the joint matrices descriptor sets and the storage descriptor sets in a map using the model identifier as the key. This will be used later on when rendering. To summarize, this method creates the required descriptor sets that are common to all the entities which use this animated model.

The records mentioned before are defined as inner classes:
```java
public class AnimationComputeActivity {
    ...
    record MeshDescriptorSets(DescriptorSet srcDescriptorSet, int workSize,
                              DescriptorSet weightsDescriptorSet) {
    }

    record ModelDescriptorSets(List<MeshDescriptorSets> meshesDescriptorSets,
                               List<List<DescriptorSet>> jointMatricesBufferDescriptorSets) {
    }
}
```

As it has been mentioned several times before, while animating, we need to dump the results of the animation to a buffer. That data needs to be unique per entity associated to an animation model (the entities may start animations at different stages or at a different pace). To create those resources, the `AnimationComputeActivity` class defines the following method:
```java
public class AnimationComputeActivity {
    ...
    public void registerEntity(VulkanModel vulkanModel, Entity entity) {
        List<EntityAnimationBuffer> bufferList = new ArrayList<>();
        entityAnimationsBuffers.put(entity.getId(), bufferList);
        for (VulkanModel.VulkanMaterial material : vulkanModel.getVulkanMaterialList()) {
            for (VulkanModel.VulkanMesh mesh : material.vulkanMeshList()) {
                VulkanBuffer animationBuffer = new VulkanBuffer(device, mesh.verticesBuffer().getRequestedSize(),
                        VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
                DescriptorSet descriptorSet = new DescriptorSet.StorageDescriptorSet(descriptorPool,
                        storageDescriptorSetLayout, animationBuffer, 0);
                bufferList.add(new EntityAnimationBuffer(animationBuffer, descriptorSet));
            }
        }
    }
    ...
}
```

This method, creates one storage buffer for each of the meshes that are part of the model associated to an entity to hold the transformed data according to the animation frame. Please note that we need to use the flag `VK_BUFFER_USAGE_STORAGE_BUFFER_BIT` when creating the buffer, so we can use it in the compute shader. It also creates a descriptor set for that buffer and stores all those objects under an `EntityAnimationBuffer` record (defined as an inner class). All those elements are grouped, using a map, around the entity identifier. Unlike the `registerModels` method, this method creates data that is not shared between the models, it is unique per entity. The `AnimationComputeActivity` class also provides a method to access that structure, since those buffers will be used to render the entities in the geometry and shader phases.
```java
public class AnimationComputeActivity {
    ...
    public Map<String, List<EntityAnimationBuffer>> getEntityAnimationsBuffers() {
        return entityAnimationsBuffers;
    }
    ...
}
```

It is turn now to present the `recordCommandBuffer` method which will be responsible of recording the dispatching commands that will be executed through the compute shader pipeline to calculate the animations. 
```java
public class AnimationComputeActivity {
    ...
    public void recordCommandBuffer(List<VulkanModel> vulkanModelList) {
        fence.fenceWait();
        fence.reset();

        commandBuffer.reset();
        commandBuffer.beginRecording();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBuffer cmdHandle = commandBuffer.getVkCommandBuffer();

            vkCmdPipelineBarrier(cmdHandle, VK_PIPELINE_STAGE_VERTEX_INPUT_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    0, memoryBarrier.getVkMemoryBarrier(), null, null);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_COMPUTE, computePipeline.getVkPipeline());

            LongBuffer descriptorSets = stack.mallocLong(4);

            for (VulkanModel vulkanModel : vulkanModelList) {
                String modelId = vulkanModel.getModelId();
                List<Entity> entities = scene.getEntitiesByModelId(modelId);
                if (entities.isEmpty() || !vulkanModel.hasAnimations()) {
                    continue;
                }

                ModelDescriptorSets modelDescriptorSets = modelDescriptorSetsMap.get(modelId);
                int meshCount = 0;
                for (VulkanModel.VulkanMaterial material : vulkanModel.getVulkanMaterialList()) {
                    for (VulkanModel.VulkanMesh mesh : material.vulkanMeshList()) {
                        MeshDescriptorSets meshDescriptorSets = modelDescriptorSets.meshesDescriptorSets.get(meshCount);
                        descriptorSets.put(0, meshDescriptorSets.srcDescriptorSet.getVkDescriptorSet());
                        descriptorSets.put(1, meshDescriptorSets.weightsDescriptorSet.getVkDescriptorSet());

                        for (Entity entity : entities) {
                            List<EntityAnimationBuffer> animationsBuffer = entityAnimationsBuffers.get(entity.getId());
                            EntityAnimationBuffer entityAnimationBuffer = animationsBuffer.get(meshCount);
                            descriptorSets.put(2, entityAnimationBuffer.descriptorSet().getVkDescriptorSet());

                            Entity.EntityAnimation entityAnimation = entity.getEntityAnimation();
                            if (!entityAnimation.isStarted()) {
                                continue;
                            }
                            DescriptorSet jointMatricesDescriptorSet = modelDescriptorSets.jointMatricesBufferDescriptorSets.
                                    get(entityAnimation.getAnimationIdx()).get(entityAnimation.getCurrentFrame());
                            descriptorSets.put(3, jointMatricesDescriptorSet.getVkDescriptorSet());

                            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_COMPUTE,
                                    computePipeline.getVkPipelineLayout(), 0, descriptorSets, null);

                            vkCmdDispatch(cmdHandle, meshDescriptorSets.numVertices(), 1, 1);
                        }
                        meshCount++;
                    }
                }
            }
        }
        commandBuffer.endRecording();
    }
    ...
}
```

The code is similar to the recording methods in the geometry, shadow and lighting phases. We first wait for the fence to prevent using the command while in use. Once we start the recording we first submit the global memory barrier, waiting for the vertex stage to complete before starting commands that will go through the compute stage. After that, we iterate over the models and their meshes, setting the appropriate descriptor sets that will hold the binding pose data and the weights list. For each associated entity we set up the descriptor linked to the storage buffer that will hold the results, and the joint matrices list associated to the specific frame used to render the entity. Finally we call the `vkCmdDispatch` function to dispatch the compute shader execution.

Finally, the `AnimationComputeActivity` class defines a `submit` method that just submits the recorded commands to the compute queue. In this case we do not use any synchronization semaphores, since we are syncing using barriers.
```java
public class AnimationComputeActivity {
    ...
    public void submit() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            computeQueue.submit(stack.pointers(commandBuffer.getVkCommandBuffer()),
                    null,
                    null,
                    null,
                    fence);
        }
    }
    ...
}
```

The next step is to write the compute shader which performs the calculations. The computer shader (`animations_comp.glsl`) starts like this:
```glsl
#version 450

const int MAX_JOINTS = 150;

layout (std430, set=0, binding=0) readonly buffer srcBuf {
    float data[];
} srcVector;

layout (std430, set=1, binding=0) readonly buffer weightsBuf {
    float data[];
} weightsVector;

layout (std430, set=2, binding=0) buffer dstBuf {
    float data[];
} dstVector;

layout (local_size_x=1, local_size_y=1, local_size_z=1) in;

layout(set = 3, binding = 0) uniform JointMatricesUniform {
    mat4 jointMatrices[MAX_JOINTS];
} jointMatricesUniform;
...
```

The `srcVector` is the storage buffer that contains binding pose data (positions, texture coordinates, normals, bitangents and tangents). It is a readonly buffer since we will not writing to it. The `weightsVector` is also a readonly buffer that contains the weights associated to each vertex. The `dstVector` is the storage buffer that will hold our results, it will contain the positions, texture coordinates, normals, bitangents and tangents transformed according to the animation. Finally, the `jointMatricesUniform`, holds the list of transformation matrices applicable to each joint for a specific frame. Going back to the shaders, the `main` method starts like this:
```glsl
void main()
{
    int baseIdxWeightsBuf  = int(gl_GlobalInvocationID.x) * 8;
    vec4 weights = vec4(weightsVector.data[baseIdxWeightsBuf], weightsVector.data[baseIdxWeightsBuf + 1], weightsVector.data[baseIdxWeightsBuf + 2], weightsVector.data[baseIdxWeightsBuf + 3]);
    ivec4 joints = ivec4(weightsVector.data[baseIdxWeightsBuf + 4], weightsVector.data[baseIdxWeightsBuf + 5], weightsVector.data[baseIdxWeightsBuf + 6], weightsVector.data[baseIdxWeightsBuf + 7]);

    ...
}
```

The `main` method may seem too verbose, but it is not so complex indeed. First, we use the built-in variable `gl_GlobalInvocationID` to get invocation number that we are in (the shader will be invoked as many times as vertices has the mesh to be animated). We will use that value to select the appropriate data from the storage buffer. The weights storage buffer will have 4 floats per vertex which will contain the weight factors that apply to a vertex and 4 integers that will point to the joint index that the weight factor should be applied. Therefore, the weights buffer can be divided in slots of 8 floats (assuming an integer occupies the same size as a float). We get the weights factors and the joint indices into 4D vectors.

Now we will examine how the vertex positions are transformed:
```glsl
void main()
{
    ...
    int baseIdxSrcBuf = int(gl_GlobalInvocationID.x) * 14;
    vec4 position = vec4(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1], srcVector.data[baseIdxSrcBuf + 2], 1);
    position =
    weights.x * jointMatricesUniform.jointMatrices[joints.x] * position +
    weights.y * jointMatricesUniform.jointMatrices[joints.y] * position +
    weights.z * jointMatricesUniform.jointMatrices[joints.z] * position +
    weights.w * jointMatricesUniform.jointMatrices[joints.w] * position;
    dstVector.data[baseIdxSrcBuf] = position.x / position.w;
    dstVector.data[baseIdxSrcBuf + 1] = position.y / position.w;
    dstVector.data[baseIdxSrcBuf + 2] = position.z / position.w;
    ...
}
```

After that, we get the vertex positions form the storage buffer that contains the vertex data for the bind position. That buffer can be split into slices of 14 floats: 3 floats for vertex positions, 3 for normal coordinates, 3 for tangent coordinates, 3 for bitangent coordinates and 2 for texture coordinates. Once we get the vertex position, we modify those coordinates by applying a modulation factor which is derived from multiplying the weight factor by the joint transformation matrix of the associated matrix.
```glsl
void main()
{
    ...
    int baseIdxSrcBuf = int(gl_GlobalInvocationID.x) * 14;
    vec4 position = vec4(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1], srcVector.data[baseIdxSrcBuf + 2], 1);
    position =
    weights.x * jointMatricesUniform.jointMatrices[joints.x] * position +
    weights.y * jointMatricesUniform.jointMatrices[joints.y] * position +
    weights.z * jointMatricesUniform.jointMatrices[joints.z] * position +
    weights.w * jointMatricesUniform.jointMatrices[joints.w] * position;
    dstVector.data[baseIdxSrcBuf] = position.x / position.w;
    dstVector.data[baseIdxSrcBuf + 1] = position.y / position.w;
    dstVector.data[baseIdxSrcBuf + 2] = position.z / position.w;
    ...
}
```

The same process is applied to the normal, tangent and bitangent.
```glsl
void main()
{
    ...
    baseIdxSrcBuf += 3;
    vec4 normal = vec4(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1], srcVector.data[baseIdxSrcBuf + 2], 0);
    normal =
    weights.x * jointMatricesUniform.jointMatrices[joints.x] * normal +
    weights.y * jointMatricesUniform.jointMatrices[joints.y] * normal +
    weights.z * jointMatricesUniform.jointMatrices[joints.z] * normal +
    weights.w * jointMatricesUniform.jointMatrices[joints.w] * normal;
    dstVector.data[baseIdxSrcBuf] = normal.x / normal.w;
    dstVector.data[baseIdxSrcBuf + 1] = normal.y / normal.w;
    dstVector.data[baseIdxSrcBuf + 2] = normal.z / normal.w;

    baseIdxSrcBuf += 3;
    vec4 tangent = vec4(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1], srcVector.data[baseIdxSrcBuf + 2], 0);
    tangent =
    weights.x * jointMatricesUniform.jointMatrices[joints.x] * tangent +
    weights.y * jointMatricesUniform.jointMatrices[joints.y] * tangent +
    weights.z * jointMatricesUniform.jointMatrices[joints.z] * tangent +
    weights.w * jointMatricesUniform.jointMatrices[joints.w] * tangent;
    dstVector.data[baseIdxSrcBuf] = tangent.x / tangent.w;
    dstVector.data[baseIdxSrcBuf + 1] = tangent.y / tangent.w;
    dstVector.data[baseIdxSrcBuf + 2] = tangent.z / tangent.w;

    baseIdxSrcBuf += 3;
    vec4 bitangent = vec4(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1], srcVector.data[baseIdxSrcBuf + 2], 0);
    bitangent =
    weights.x * jointMatricesUniform.jointMatrices[joints.x] * bitangent +
    weights.y * jointMatricesUniform.jointMatrices[joints.y] * bitangent +
    weights.z * jointMatricesUniform.jointMatrices[joints.z] * bitangent +
    weights.w * jointMatricesUniform.jointMatrices[joints.w] * bitangent;
    dstVector.data[baseIdxSrcBuf] = bitangent.x / bitangent.w;
    dstVector.data[baseIdxSrcBuf + 1] = bitangent.y / bitangent.w;
    dstVector.data[baseIdxSrcBuf + 2] = bitangent.z / bitangent.w;
    ...
}
```
Finally, we just copy the texture coordinates, there is no need to transform that.
```glsl
void main()
{
    ...
    baseIdxSrcBuf += 3;
    vec2 textCoords = vec2(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1]);
    dstVector.data[baseIdxSrcBuf] = textCoords.x;
    dstVector.data[baseIdxSrcBuf + 1] = textCoords.y;
}
```

## Updates on geometry rendering

Let us review now the changes in the geometry render phase. The only affected class will be the `GeometryRenderActivity` one. We will go step by step. First of all, in order for the geometry shader phase to wait for the compute shader to finish, we will need a global memory barrier. Therefore, we need to create a new attribute for that and initialize it in the constructor.
```java
public class GeometryRenderActivity {
    ...
    private MemoryBarrier memoryBarrier;
    ...
    public GeometryRenderActivity(SwapChain swapChain, CommandPool commandPool, PipelineCache pipelineCache, Scene scene) {
        ...
        memoryBarrier = new MemoryBarrier(VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT);
    }
    ...
}
``` 

That barrier will be used when recording the commands that will render the scene, therefore, we need to modify the `recordCommandBuffers` method and submit the barrier. It will  state that in order to read memory contents (the `VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT` flag that we used when creating the barrier) form the vertex shader, we need the compute shader to finish writing to memory:
```java
public class GeometryRenderActivity {
    ...
    public void recordCommandBuffers(CommandBuffer commandBuffer, List<VulkanModel> vulkanModelList,
                                     Map<String, List<AnimationComputeActivity.EntityAnimationBuffer>> entityAnimationsBuffers) {
        ...
            VkCommandBuffer cmdHandle = commandBuffer.getVkCommandBuffer();

            vkCmdPipelineBarrier(cmdHandle, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_VERTEX_INPUT_BIT,
                    0, memoryBarrier.getVkMemoryBarrier(), null, null);

            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE);
        ...
            recordEntities(stack, cmdHandle, descriptorSets, vulkanModelList, entityAnimationsBuffers);
        ...
    }
    ...
}
```

You can see that the `recordCommandBuffers` has a new reference to the buffers that hold the animated vertices for each of the entities. This will be used in the `recordEntities` method. In this method, we just need to check if the entity is related to a model that has animations or not. If so, instead of using the data associated to the meshes of the model, we use the buffer associated to the animation for that entity.
```java
    private void recordEntities(MemoryStack stack, VkCommandBuffer cmdHandle, LongBuffer descriptorSets,
                                List<VulkanModel> vulkanModelList,
                                Map<String, List<AnimationComputeActivity.EntityAnimationBuffer>> entityAnimationsBuffers) {
        ...
        for (VulkanModel vulkanModel : vulkanModelList) {
            ...
            int meshCount = 0;
            for (VulkanModel.VulkanMaterial material : vulkanModel.getVulkanMaterialList()) {
                ...
                for (VulkanModel.VulkanMesh mesh : material.vulkanMeshList()) {
                    if (!vulkanModel.hasAnimations()) {
                        vertexBuffer.put(0, mesh.verticesBuffer().getBuffer());
                        vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                    }
                    vkCmdBindIndexBuffer(cmdHandle, mesh.indicesBuffer().getBuffer(), 0, VK_INDEX_TYPE_UINT32);

                    for (Entity entity : entities) {
                        if (vulkanModel.hasAnimations()) {
                            List<AnimationComputeActivity.EntityAnimationBuffer> animationsBuffer = entityAnimationsBuffers.get(entity.getId());
                            AnimationComputeActivity.EntityAnimationBuffer entityAnimationBuffer = animationsBuffer.get(meshCount);
                            vertexBuffer.put(0, entityAnimationBuffer.verticesBuffer().getBuffer());
                            vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                        }
                        descriptorSets.put(2, textureDescriptorSet.getVkDescriptorSet());
                        descriptorSets.put(3, normalMapDescriptorSet.getVkDescriptorSet());
                        descriptorSets.put(4, metalRoughDescriptorSet.getVkDescriptorSet());
                        vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                                pipeLine.getVkPipelineLayout(), 0, descriptorSets, dynDescrSetOffset);

                        VulkanUtils.setMatrixAsPushConstant(pipeLine, cmdHandle, entity.getModelMatrix());
                        vkCmdDrawIndexed(cmdHandle, mesh.numIndices(), 1, 0, 0, 0);
                    }
                    meshCount++;
                }
            }
            ...
        }
        ...
    }
    ...
}
```

Note that we do not need to modify the geometry shaders. Animated models are rendered exactly the same way as non animated ways. The only thing that we need to do is select the appropriate buffer.

## Updates on shadow rendering

We need also to update the code that renders shadow cascades. The changes are quite similar than in the geometry phase, we just need to select the buffer that holds the vertices transformed according to the data of a specific key frame in the `ShadowRenderActivity` class. In this case, we do not need to set up a memory barrier, this was already done when recording the geometry phase commands.
```java
public class ShadowRenderActivity {
    ...
    public void recordCommandBuffers(CommandBuffer commandBuffer, List<VulkanModel> vulkanModelList,
                                     Map<String, List<AnimationComputeActivity.EntityAnimationBuffer>> entityAnimationsBuffers) {
        ...
            recordEntities(stack, cmdHandle, vulkanModelList, entityAnimationsBuffers);
        ...
    }


    private void recordEntities(MemoryStack stack, VkCommandBuffer cmdHandle, List<VulkanModel> vulkanModelList,
                                Map<String, List<AnimationComputeActivity.EntityAnimationBuffer>> entityAnimationsBuffers) {
        ...
        LongBuffer vertexBuffer = stack.mallocLong(1);
        for (VulkanModel vulkanModel : vulkanModelList) {
            ...
            int meshCount = 0;
            for (VulkanModel.VulkanMaterial material : vulkanModel.getVulkanMaterialList()) {
                for (VulkanModel.VulkanMesh mesh : material.vulkanMeshList()) {
                    if (!vulkanModel.hasAnimations()) {
                        vertexBuffer.put(0, mesh.verticesBuffer().getBuffer());
                        vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                    }
                    vkCmdBindIndexBuffer(cmdHandle, mesh.indicesBuffer().getBuffer(), 0, VK_INDEX_TYPE_UINT32);

                    for (Entity entity : entities) {
                        setPushConstant(pipeLine, cmdHandle, entity.getModelMatrix());
                        if (vulkanModel.hasAnimations()) {
                            List<AnimationComputeActivity.EntityAnimationBuffer> animationsBuffer = entityAnimationsBuffers.get(entity.getId());
                            AnimationComputeActivity.EntityAnimationBuffer entityAnimationBuffer = animationsBuffer.get(meshCount);
                            vertexBuffer.put(0, entityAnimationBuffer.verticesBuffer().getBuffer());
                            vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                        }
                        vkCmdDrawIndexed(cmdHandle, mesh.numIndices(), 1, 0, 0, 0);
                    }
                    meshCount++;
                }
            }
        }
    }
    ...
}
```

As in the geometry phase, the shaders do not need to be changed.

## Final updates

We have almost finished with the changes required in the code base to use animations. The next step is to modify the `Render` class. First we create an instance of the `AnimationComputeActivity` class in the constructor and invoke its `cleanup` method when freeing resources.
```java
public class Render {
    ...
    private AnimationComputeActivity animationComputeActivity;
    ...
    public Render(Window window, Scene scene) {
        ...
        animationComputeActivity = new AnimationComputeActivity(commandPool, pipelineCache, scene);
    }

    public void cleanup() {
        ...
        animationComputeActivity.cleanup();
        ...
    }
    ...
}
```

After that we need to create a new method, named `loadAnimation`, to trigger the creation of the required resources to animate a specific entity. This method, tries to get the model information associated to that entity and delegates the resource creation to the `registerEntity` method of the `AnimationComputeActivity` instance. As it has been explained above, when using non animated models, we can share the same model data for all the entities that share that some model. However, for animated entities, we need to create separate buffers for each specific instance, since their animations need to be independent (each entity may be in a different animation state). In the `loadModels` method of the `Render` class we need to call also the `registerModels` method in the  `AnimationComputeActivity` instance to setup the animation data used for that model (as described earlier).
```java
public class Render {
    ...
    public void loadAnimation(Entity entity) {
        String modelId = entity.getModelId();
        Optional<VulkanModel> optModel = vulkanModels.stream().filter(m -> m.getModelId().equals(modelId)).findFirst();
        if (optModel.isEmpty()) {
            throw new RuntimeException("Could not find model [" + modelId + "]");
        }
        VulkanModel vulkanModel = optModel.get();
        if (!vulkanModel.hasAnimations()) {
            throw new RuntimeException("Model [" + modelId + "] does not define animations");
        }

        animationComputeActivity.registerEntity(vulkanModel, entity);
    }

    public void loadModels(List<ModelData> modelDataList) {
        ...
        animationComputeActivity.registerModels(vulkanModels);
    }
    ...
}
```

Finally, we need to modify the `render` method to record the compute shader commands to animate the buffers associated to entities which have animations. As we have nmmentioned in the introduction, we have tried to keep the code as simple as possible. However, animation buffers should only be updated attending to the duration of the different key frames and the desired interpolating frequency. Also, compute commands recording could be performed asynchronously in a separate thread. In order to keep this example simple, we will just update those buffers in each render cycle.
```java
public class Render {
    ...
    public void render(Window window, Scene scene) {
        ...
        animationComputeActivity.recordCommandBuffer(vulkanModels);
        animationComputeActivity.submit();

        CommandBuffer commandBuffer = geometryRenderActivity.beginRecording();
        geometryRenderActivity.recordCommandBuffers(commandBuffer, vulkanModels, animationComputeActivity.getEntityAnimationsBuffers());
        shadowRenderActivity.recordCommandBuffers(commandBuffer, vulkanModels, animationComputeActivity.getEntityAnimationsBuffers());
        ...
    }
    ...
}
```

The last step is to load an animated model in the `Main` class. We just need to load it as in the case of static models, but specifying that it will contain animations. We need also to invoke the `Render` class `loadAnimation` method to register the animation for an entity. In the `handleInput` method we will use to space bar to pause / resume the animation and automatically select the next key frame, when animation is not paused, in each update cycle. 
```java
public class Main implements IAppLogic {
    ...
    private Entity bobEntity;
    ...
    public void handleInput(Window window, Scene scene, long diffTimeMilisec) {
        ...
        if (window.isKeyPressed(GLFW_KEY_SPACE)) {
            bobEntity.getEntityAnimation().setStarted(!bobEntity.getEntityAnimation().isStarted());
        }
        ...
        Entity.EntityAnimation entityAnimation = bobEntity.getEntityAnimation();
        if (entityAnimation.isStarted()) {
            int currentFrame = Math.floorMod(entityAnimation.getCurrentFrame() + 1, maxFrames);
            entityAnimation.setCurrentFrame(currentFrame);
        }
    }
    ...
    public void init(Window window, Scene scene, Render render) {
        ...
        ModelData sponzaModelData = ModelLoader.loadModel(sponzaModelId, "resources/models/sponza/Sponza.gltf",
                "resources/models/sponza", false);
        ...
        String bobModelId = "bob-model";
        ModelData bobModelData = ModelLoader.loadModel(bobModelId, "resources/models/bob/boblamp.md5mesh",
                "resources/models/bob", true);
        maxFrames = bobModelData.getAnimationsList().get(0).frames().size();
        modelDataList.add(bobModelData);
        bobEntity = new Entity("BobEntity", bobModelId, new Vector3f(0.0f, 0.0f, 0.0f));
        bobEntity.setScale(0.04f);
        bobEntity.getRotation().rotateY((float) Math.toRadians(-90.0f));
        bobEntity.updateModelMatrix();
        bobEntity.setEntityAnimation(new Entity.EntityAnimation(true, 0, 0));
        scene.addEntity(bobEntity);

        render.loadModels(modelDataList);
        render.loadAnimation(bobEntity);
        ...
    }
    ...
}
```

We are now done with the changes, you should now be able to see the scene with shadows applied, as in the following screenshot:

<img src="screen-shot.gif" title="" alt="Screen Shot" data-align="center">

[Next chapter](../chapter-15/chapter-15.md)