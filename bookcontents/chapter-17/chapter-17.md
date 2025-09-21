# Chapter 17 - Animations

In this chapter we will add support for skeletal animations using compute shaders to perform the required transformations to animate a model. By doing so, we will handle static and animated models in the scene render stage exactly the same way. The compute shader will perform the required transformations and wil dump the results in a buffer. By doing that way, we will not need to change a line of our shaders, we will just be accessing buffers that have vertex information with the same layout. Please keep in mind that, in order to keep this example as simple as possible, we will simplify the animation mechanism, for example, we will not be interpolating between animation key frames and we will not control animation duration.

You can find the complete source code for this chapter [here](../../booksamples/chapter-17).

## Skeletal animation introduction

In skeletal animation the way a model is transformed to play an animation is defined by its underlying skeleton. A skeleton is nothing more than a hierarchy of special points
called joints. These joints are organized in a tree-like structure, a joint can have a parent and several child joints. In addition to that, the final position of each joint
is affected by the position of their parents. For instance, think of a wrist: the position of a wrist is modified if a character moves the elbow and also if it moves the shoulder.

Joints do not need to represent a physical bone or articulation: they are artifacts that allow the creatives to model an animation (we may use sometimes the terms bone and joint to refer to the same thing). The models still have vertices that define the different positions, but, in skeletal animation, vertices are drawn based on the position of
the joints they are related to and modulated by a set of weights. If we draw a model using just the vertices, without taking into consideration the joints, we would get a 3D model in what is called the bind pose. Each animation is divided into key frames which basically describes the transformations that should be applied to each joint. By changing those transformations, changing those key frames, along time, we are able to animate the model. Those transformations are based on 4x4 matrices which model the displacement and rotation of each joint according to the hierarchy (basically each joint must accumulate the transformations defined by its parents).

If you are reading this, you might probably already know the fundamentals of skeletal animations. The purpose of this chapter is not to explain this in detail but to show an example on how this can be implemented using Vulkan with compute shaders. If you need all the details of skeletal animations you can check this [excellent tutorial](http://ogldev.atspace.co.uk/www/tutorial38/tutorial38.html).

## Loading the models

We need to modify the code that loads 3D models to support animations. The first step is to modify the `ModelData` class to store the data required to animate models:
```java
package org.vulkanb.eng.model;

import java.util.List;

public record ModelData(String id, List<MeshData> meshes, String vtxPath, String idxPath, List<AnimMeshData> animMeshes,
                        List<Animation> animations) {
}
```
The new `animMeshes` attribute is the equivalent of the `meshes` one. That list will contain an entry for each mesh storing the relevant data for animated models. In this case, that data is grouped under the `AnimMeshData` and contains two arrays that will contain the weights that will modulate the transformations applied to the joints related to each vertex (related by their identifier in the hierarchy). That data is common to all the animations supported by the model, since it is related to the model structure itself, its skeleton. The `animations` attribute holds the list of animations defined for a model. An animation is described by the `Animation` record and consists on a name the duration of the animation (in milliseconds) and the data of the key frames that compose the animation. Key frame data is defined by the `AnimatedFrame` record which contains the transformation matrices for each of the model joints for that specific frame. Therefore, in order to load animated models we just need to get the additional structural data for mesh (weights and the joints they apply to) and the transformation matrices for each of those joints per animation key frame.

Let's define the new classes. We will start with `AnimMeshData` one:

```java
package org.vulkanb.eng.model;

public record AnimMeshData(float[] weights, int[] boneIds) {
}
```

As you can see is just a `record` that contains an array of floats for the weights associated to a bone (or joint) and and array of identifiers for these bones / joints.
The `Animation` class is defined like this:

```java
package org.vulkanb.eng.model;

import java.util.List;

public record Animation(String name, float frameMillis, List<AnimatedFrame> frames) {
}
```

It is just another `record` that defines a name, the milliseconds that each frame should last and a list of frame data modeled by the `AnimatedFrame` class:

```java
package org.vulkanb.eng.model;

import org.joml.Matrix4f;

public record AnimatedFrame(Matrix4f[] jointMatrices) {
}
```

A frame is basically a list of transformation matrices associated to each joint / bone. These matrices will model how each of these joints will be modified (translated,
rotated and scaled) according to the specific frame. Each of these joint / bones will affect the associated vertices through a weight. 

Let's review now the changes in the `ModelGenerator` class:

```java
public class ModelGenerator {

    private static final Pattern EMBED_TEXT_ID = Pattern.compile("\\*([0-9]+)");
    private static final int FLAGS = aiProcess_GenSmoothNormals | aiProcess_JoinIdenticalVertices |
            aiProcess_Triangulate | aiProcess_FixInfacingNormals | aiProcess_CalcTangentSpace;
    private static final Matrix4f IDENTITY_MATRIX = new Matrix4f();

    @Parameter(names = "-a", description = "Animated model")
    private boolean animation;
    ...
}
```
We remove the flag `aiProcess_PreTransformVertices` since it will not work with animated models. We will use it only for non animated ones. Therefore, we need a new command
line argument to know in advance that we will be dealing with animated models (`-a`). 

These are the changes in the `mainProcessing` method:

```java
public class ModelGenerator {
    ...
    private void mainProcessing() throws IOException {
        ...
        AIScene aiScene = aiImportFile(modelPath, FLAGS | (animation ? aiProcess_LimitBoneWeights : aiProcess_PreTransformVertices));
        ...
        int numAnimations = aiScene.mNumAnimations();
        List<AnimMeshData> animMeshDataList = null;
        List<Animation> animations = null;
        if (numAnimations > 0) {
            Logger.debug("Processing animations");
            List<Bone> boneList = new ArrayList<>();
            animMeshDataList = new ArrayList<>();
            for (int i = 0; i < numMeshes; i++) {
                AIMesh aiMesh = AIMesh.create(aiMeshes.get(i));
                AnimMeshData animMeshData = processBones(aiMesh, boneList);
                animMeshDataList.add(animMeshData);
            }

            Node rootNode = buildNodesTree(aiScene.mRootNode(), null);
            Matrix4f globalInverseTransformation = toMatrix(aiScene.mRootNode().mTransformation()).invert();
            animations = processAnimations(aiScene, boneList, rootNode, globalInverseTransformation);
        }

        var model = new ModelData(modelId, meshList, modelBinData.getVtxFilePath(), modelBinData.getIdxFilePath(),
                animMeshDataList, animations);

        String outModelFile = modelPath.substring(0, modelPath.lastIndexOf('.')) + ".json";
        ...
    }
    ...
}
```

As you can see, we only use the `aiProcess_PreTransformVertices` to load non animated models using Assimp. For animated models we use the `aiProcess_LimitBoneWeights` flag
that limits the number of bones simultaneously affecting a single vertex to a maximum value (the default maximum values is `4`). If the model has animations, we first process bones structure through the `processBones` method which returns an instance of the `AnimMeshData` which contains the weights associated to the bones in the model.
Each bone / joint will have different impact on the animation based on weights. After that, we construct a structure of nodes. That will be the tree like skeleton structure
(the bones hierarchy) which contains transformation matrices for each of the joint / bones and will allow us to calculate the animation

The `processBones` method is defined like this:
```java
public class ModelGenerator {
    ...
    private static AnimMeshData processBones(AIMesh aiMesh, List<Bone> boneList) {
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
            for (int j = 0; j < EngCfg.MAX_WEIGHTS; j++) {
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

        return new AnimMeshData(listFloatToArray(weights), listIntToArray(boneIds));
    }
    ...
}
```
In that `processBones` method, we first construct a list of bones. Each bone will have an identifier which will be used later on to relate them to the wights to be applied for each vertex. That information is stored in the record `AnimMeshData`. The `Bone` and `VertexWeight` data are defined like this:

```java
package org.vulkanb.eng.model;

import org.joml.Matrix4f;

public record Bone(int boneId, String boneName, Matrix4f offsetMatrix) {
}
```

```java
package org.vulkanb.eng.model;

public record VertexWeight(int boneId, int vertexId, float weight) {
}
```

The `buildNodesTree` method is quite simple, It just traverses the nodes hierarchy starting from the root node constructing a tree of nodes:
```java
public class ModelGenerator {
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

The `Node` class is defined like this:

```java
package org.vulkanb.eng.model;

import org.joml.Matrix4f;

import java.util.*;

public class Node {

    private final List<Node> children;
    private final String name;
    private final Matrix4f nodeTransformation;
    private final Node parent;

    public Node(String name, Node parent, Matrix4f nodeTransformation) {
        this.name = name;
        this.parent = parent;
        this.nodeTransformation = nodeTransformation;
        this.children = new ArrayList<>();
    }

    public void addChild(Node node) {
        this.children.add(node);
    }

    public List<Node> getChildren() {
        return children;
    }

    public String getName() {
        return name;
    }

    public Matrix4f getNodeTransformation() {
        return nodeTransformation;
    }

    public Node getParent() {
        return parent;
    }
}
```

It is a way to model a tree of `Node` instances. Each node is defined by the its name, its transformation, the parent and child nodes.

Back to the `ModelGenerator` class, let’s review the `processAnimations` method, which is defined like this:
```java
public class ModelGenerator {
    ...
    private static List<Animation> processAnimations(AIScene aiScene, List<Bone> boneList,
                                                     Node rootNode, Matrix4f globalInverseTransformation) {
        List<Animation> animations = new ArrayList<>();

        int maxJointsMatricesLists = EngCfg.getInstance().getMaxJointsMatricesLists();
        // Process all animations
        int numAnimations = aiScene.mNumAnimations();
        PointerBuffer aiAnimations = aiScene.mAnimations();
        for (int i = 0; i < numAnimations; i++) {
            AIAnimation aiAnimation = AIAnimation.create(aiAnimations.get(i));
            int maxFrames = calcAnimationMaxFrames(aiAnimation);
            float frameMillis = (float) (aiAnimation.mDuration() / aiAnimation.mTicksPerSecond());
            List<AnimatedFrame> frames = new ArrayList<>();
            Animation animation = new Animation(aiAnimation.mName().dataString(), frameMillis, frames);
            animations.add(animation);

            for (int j = 0; j < maxFrames; j++) {
                Matrix4f[] jointMatrices = new Matrix4f[maxJointsMatricesLists];
                Arrays.fill(jointMatrices, IDENTITY_MATRIX);
                AnimatedFrame animatedFrame = new AnimatedFrame(jointMatrices);
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

This method returns a `List` of `Animation` instances (Remember that a model can have more than one animation). For each of those animations we construct a list of animation frames (`AnimatedFrame` instances), which contain essentially a list of the transformation matrices to be applied to each of the bones that compose the model. For each of the animations, we calculate the maximum number of frames by calling the method `calcAnimationMaxFrames`, which is defined like this: 
```java
public class ModelGenerator {
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
public class ModelGenerator {
    ...
    private static void buildFrameMatrices(AIAnimation aiAnimation, List<Bone> boneList, AnimatedFrame animatedFrame,
                                           int frame, Node node, Matrix4f parentTransformation, Matrix4f globalInverseTransform) {
        String nodeName = node.getName();
        AINodeAnim aiNodeAnim = findAIAnimNode(aiAnimation, nodeName);
        Matrix4f nodeTransform = node.getNodeTransformation();
        if (aiNodeAnim != null) {
            nodeTransform = buildNodeTransformationMatrix(aiNodeAnim, frame);
        }
        Matrix4f nodeGlobalTransform = new Matrix4f(parentTransformation).mul(nodeTransform);

        List<Bone> affectedBones = boneList.stream().filter(b -> b.boneName().equals(nodeName)).toList();
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

The `findAIAnimNode` is defined like this:
```java
public class ModelGenerator {
    ...
    private static AINodeAnim findAIAnimNode(AIAnimation aiAnimation, String nodeName) {
        AINodeAnim result = null;
        int numAnimNodes = aiAnimation.mNumChannels();
        PointerBuffer aiChannels = aiAnimation.mChannels();
        for (int i = 0; i < numAnimNodes; i++) {
            AINodeAnim aiNodeAnim = AINodeAnim.create(aiChannels.get(i));
            if (nodeName.equals(aiNodeAnim.mNodeName().dataString())) {
                result = aiNodeAnim;
                break;
            }
        }
        return result;
    }
    ...
}
```

It basically, just iterates over the animation channels list to find the animation node information associated to the id we are looking for. After that, we iterate over the children nodes, using the node transformation matrix as the parent matrix for those child nodes.

Let’s review the `buildNodeTransformationMatrix` method:
```java
public class ModelGenerator {
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
public class ModelGenerator {
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

We need also to create two new methods to transform a `List` of `Integer` and `Float`data to the equivalent arrays:
```java
public class ModelGenerator {
    ...
    private static float[] listFloatToArray(List<Float> list) {
        int size = list != null ? list.size() : 0;
        float[] floatArr = new float[size];
        for (int i = 0; i < size; i++) {
            floatArr[i] = list.get(i);
        }
        return floatArr;
    }

    private static int[] listIntToArray(List<Integer> list) {
        return list.stream().mapToInt((Integer v) -> v).toArray();
    }
    ...
}
```

We needed to add a new configuration property in the `EngCfg` class to define the maximum number of joint matrices lists, here are the changes:
```java
public class EngCfg {
    public static final int MAX_WEIGHTS = 4;
    ...
    private int maxJointsMatricesLists;
    ...
    private EngineProperties() {
        ...
            maxJointsMatricesLists = Integer.parseInt(props.getOrDefault("maxJointsMatricesLists", 100).toString());
        ...
    }
    ...
    public int getMaxJointsMatricesLists() {
        return maxJointsMatricesLists;
    }
    ...
}
```

After that we will create a new class named `EntityAnimation` to control then animation state of animated entities in order to to pause / resume the animation, to select
the proper animation and to select a specific key frame:

```java
package org.vulkanb.eng.scene;

public class EntityAnimation {
    private int animationIdx;
    private int currentFrame;
    private long frameStartTs;
    private boolean started;

    public EntityAnimation(boolean started, int animationIdx, int currentFrame) {
        this.started = started;
        this.animationIdx = animationIdx;
        this.currentFrame = currentFrame;
        if (started) {
            frameStartTs = System.currentTimeMillis();
        }
    }

    public int getAnimationIdx() {
        return animationIdx;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public long getFrameStartTs() {
        return frameStartTs;
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
        if (started) {
            frameStartTs = System.currentTimeMillis();
        }
    }
}
```

We will modify the `Entity` class so it can have reference to the `EntityAnimation` class for animated entities:
```java
public class Entity {
    ...
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
}
```

All that information class needs to be handled in the `VulkanModel` class so it is loaded into the GPU. The changes in this class are defined like this:
```java
public class VulkanModel {
    ...
    private final List<VulkanAnimation> vulkanAnimationList;
    ...
    public VulkanModel(String id) {
        this.id = id;
        vulkanMeshList = new ArrayList<>();
        vulkanAnimationList = new ArrayList<>();
    }

    public void addVulkanAnimation(VulkanAnimation vulkanAnimation) {
        vulkanAnimationList.add(vulkanAnimation);
    }

    public void cleanup(VkCtx vkCtx) {
        ...
        vulkanAnimationList.forEach(a -> a.cleanup(vkCtx));
    }
    ...
    public List<VulkanAnimation> getVulkanAnimationList() {
        return vulkanAnimationList;
    }
    ...
    public boolean hasAnimations() {
        return !vulkanAnimationList.isEmpty();
    }
}
```

The `VulkanMesh` class needs also to be defined in order to store a buffer which will contain the weights:

```java
package org.vulkanb.eng.graph;

import org.vulkanb.eng.graph.vk.*;

public record VulkanMesh(String id, VkBuffer verticesBuffer, VkBuffer indicesBuffer, VkBuffer weightsBuffer,
                         int numIndices, String materialdId) {
    public void cleanup(VkCtx vkCtx) {
        verticesBuffer.cleanup(vkCtx);
        indicesBuffer.cleanup(vkCtx);
        if (weightsBuffer != null) {
            weightsBuffer.cleanup(vkCtx);
        }
    }
}
```

We need to create a new class named `VulkanAnimation` which will hold the matrices associated to the bones / joints for ach of the animation frames:

```java
package org.vulkanb.eng.graph;

import org.vulkanb.eng.graph.vk.*;

import java.util.List;

public record VulkanAnimation(String name, List<VkBuffer> frameBufferList) {
    public void cleanup(VkCtx vkCtx) {
        frameBufferList.forEach(b -> b.cleanup(vkCtx));
    }
}
```

After all those changes we can modify the `ModelsCache` class. The starting point will be the `loadModels` methods.
```java
public class ModelsCache {
    ...
    public void loadModels(VkCtx vkCtx, List<ModelData> models, CmdPool cmdPool, Queue queue) {
        try {
            List<VkBuffer> stagingBufferList = new ArrayList<>();

            var cmd = new CmdBuffer(vkCtx, cmdPool, true, true);
            cmd.beginRecording();

            for (ModelData modelData : models) {
                VulkanModel vulkanModel = new VulkanModel(modelData.id());
                modelsMap.put(vulkanModel.getId(), vulkanModel);

                List<Animation> animationsList = modelData.animations();
                boolean hasAnimation = animationsList != null && !animationsList.isEmpty();
                if (hasAnimation) {
                    for (Animation animation : animationsList) {
                        List<VkBuffer> vulkanFrameBufferList = new ArrayList<>();
                        VulkanAnimation vulkanAnimation = new VulkanAnimation(animation.name(), vulkanFrameBufferList);
                        vulkanModel.addVulkanAnimation(vulkanAnimation);
                        List<AnimatedFrame> frameList = animation.frames();
                        for (AnimatedFrame frame : frameList) {
                            TransferBuffer jointMatricesBuffers = createJointMatricesBuffers(vkCtx, frame);
                            stagingBufferList.add(jointMatricesBuffers.srcBuffer());
                            jointMatricesBuffers.recordTransferCommand(cmd);
                            vulkanFrameBufferList.add(jointMatricesBuffers.dstBuffer());
                        }
                    }
                }

                DataInputStream vtxInput = new DataInputStream(new BufferedInputStream(new FileInputStream(modelData.vtxPath())));
                DataInputStream idxInput = new DataInputStream(new BufferedInputStream(new FileInputStream(modelData.idxPath())));
                // Transform meshes loading their data into GPU buffers
                int meshCount = 0;
                for (MeshData meshData : modelData.meshes()) {
                    TransferBuffer verticesBuffers = createVerticesBuffers(vkCtx, meshData, vtxInput);
                    TransferBuffer indicesBuffers = createIndicesBuffers(vkCtx, meshData, idxInput);
                    stagingBufferList.add(verticesBuffers.srcBuffer());
                    stagingBufferList.add(indicesBuffers.srcBuffer());
                    verticesBuffers.recordTransferCommand(cmd);
                    indicesBuffers.recordTransferCommand(cmd);

                    TransferBuffer weightsBuffers = null;
                    List<AnimMeshData> animMeshDataList = modelData.animMeshes();
                    if (animMeshDataList != null && !animMeshDataList.isEmpty()) {
                        weightsBuffers = createWeightsBuffers(vkCtx, animMeshDataList.get(meshCount));
                        stagingBufferList.add(weightsBuffers.srcBuffer());
                        weightsBuffers.recordTransferCommand(cmd);
                    }

                    VulkanMesh vulkanMesh = new VulkanMesh(meshData.id(), verticesBuffers.dstBuffer(),
                            indicesBuffers.dstBuffer(), weightsBuffers != null ? weightsBuffers.dstBuffer() : null,
                            meshData.idxSize() / VkUtils.INT_SIZE, meshData.materialId());
                    vulkanModel.getVulkanMeshList().add(vulkanMesh);

                    meshCount++;
                }
            }

            cmd.endRecording();
            cmd.submitAndWait(vkCtx, queue);
            cmd.cleanup(vkCtx, cmdPool);

            stagingBufferList.forEach(b -> b.cleanup(vkCtx));
        } catch (Exception excp) {
            throw new RuntimeException(excp);
        }
    }
    ...
}
```

If the model has animations, we need to create the buffers that will hold the matrices for the joint / bones matrices for each frame. We need to store also the weights buffers.

The `createJointMatricesBuffers` is defined like this:
```java
public class ModelsCache {
    ...
    private static TransferBuffer createJointMatricesBuffers(VkCtx vkCtx, AnimatedFrame frame) {
        Matrix4f[] matrices = frame.jointMatrices();
        int numMatrices = matrices.length;
        int bufferSize = numMatrices * VkUtils.MAT4X4_SIZE;

        var srcBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VMA_MEMORY_USAGE_AUTO,
                0, 0);

        long mappedMemory = srcBuffer.map(vkCtx);
        ByteBuffer matrixBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) srcBuffer.getRequestedSize());
        for (int i = 0; i < numMatrices; i++) {
            matrices[i].get(i * VkUtils.MAT4X4_SIZE, matrixBuffer);
        }
        srcBuffer.unMap(vkCtx);

        return new TransferBuffer(srcBuffer, dstBuffer);
    }
    ...
}
```

And the `createWeightsBuffers` is defined like this:
```java
public class ModelsCache {
    ...
    private static TransferBuffer createWeightsBuffers(VkCtx vkCtx, AnimMeshData animMeshData) {
        float[] weights = animMeshData.weights();
        int[] boneIds = animMeshData.boneIds();
        int bufferSize = weights.length * VkUtils.FLOAT_SIZE + boneIds.length * VkUtils.INT_SIZE;

        var srcBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                VMA_MEMORY_USAGE_AUTO, 0, 0);

        long mappedMemory = srcBuffer.map(vkCtx);
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

        srcBuffer.unMap(vkCtx);

        return new TransferBuffer(srcBuffer, dstBuffer);
    }
    ...
}
```

We need to update the `createVerticesBuffers` since we will be accessing the contents of the vertices buffer in the compute shader through storage buffers:

```java
public class ModelsCache {
    ...
     private static TransferBuffer createVerticesBuffers(VkCtx vkCtx, MeshData meshData, DataInputStream vtxInput)
            throws IOException {
        ...
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VMA_MEMORY_USAGE_AUTO,
                0, 0);
        ...
    }
    ...
}
```

Finally, we need to add a new method to access the models map:
```java
public class ModelsCache {
    ...
    public Map<String, VulkanModel> getModelsMap() {
        return modelsMap;
    }
    ...
}
```

Each animated entity will have vertices buffer per each mesh of the associated animated model. In this way, we can calculate the transformations associated to the current
animation for each entity and store the results in a dedicated buffer. If we do it this way, we can render animated entities the same way we just render non animated ones.
For static meshes we can share the mesh vertices buffer between all the entities, but for animated ones we will have a dedicated one per entity and mesh. We will create
a new class named `AnimationsCache` that will create those buffers for all the animated entities:

```java
package org.vulkanb.eng.graph;

import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.Entity;

import java.util.*;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

public class AnimationsCache {

    private final Map<String, Map<String, VkBuffer>> entitiesAnimBuffers;

    public AnimationsCache() {
        entitiesAnimBuffers = new HashMap<>();
    }

    public void cleanup(VkCtx vkCtx) {
        entitiesAnimBuffers.values().forEach(m -> m.values().forEach(b -> b.cleanup(vkCtx)));
    }

    public VkBuffer getBuffer(String entityId, String meshId) {
        return entitiesAnimBuffers.get(entityId).get(meshId);
    }

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

## Compute Shader

Prior to jumping to the code, it is necessary to briefly describe compute shaders. Compute shaders are a bit different than vertex or fragment shaders. Vertex and fragment shaders have a well defined inputs and outputs. For example, when we create a graphics pipeline, we define the structure of the input data for the vertex shader vertex shader. In addition to that, vertex shaders get invoked, "automatically", as many times as required to consume that input. In our examples, up to this point, vertex input contains vertex position, texture coordinates and the normals data. Compute shaders operate differently, they work over buffers as a whole. It is up to us to decide how it will execute and how they will operate over the data they will require to perform their computation and where the results should be stored. Compute shaders access data (for reading and writing) through storage buffers. In our case, we will store binding pose information as read only storage buffers and will store the transformed positions in a read / write storage buffer. That output buffer will later be read in the geometry phase as a regular vertex buffer.

As mentioned above, a key topic of compute shaders is how many times they should be invoked and how the work load is distributed. Compute shaders define the concept of work groups, which are a collection of of shader invocations that can be executed, potentially, in parallel. Work groups are three dimensional, so they will be defined by the triplet `(Wx, Wy, Wz)`, where each of those components must be equal to or greater than `1`.  A compute shader will execute in total `Wx*Wy*Wz` work groups. Work groups have also a size, named local size. Therefore, we can define local size as another triplet `(Lx, Ly, Lz)`. The total number of times a compute shader will be invoked will be the product `Wx*Lx*Wy*Ly*Wz*Lz`. The reason behind specifying these using three dimension parameters is because some data is handled in a more convenient way using 2D or 3D dimensions. You can think for example in a image transformation computation, we would be probably using the data of an image pixel and their neighbor pixels. We could organize the work using 2D computation parameters. In addition to that, work done inside a work group, can share same variables and resources, which may be required when processing 2D or 3D data. Inside the computer shader we will have access to pre-built variables that will identify the invocation we are in so we can properly access the data slice that we want to work with according to our needs.  

In order to support the execution of commands that will go through the compute pipeline, we need first to define a new class named `ComputePipeline` to support the creation of that type of pipelines. Compute pipelines are much simpler than graphics pipelines. Graphics pipelines have a set of fixed and programmable stages while the compute pipeline has a single programmable compute shader stage. So let's go with it:
```java
package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.nio.*;

import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class ComputePipeline {

    private final long vkPipeline;
    private final long vkPipelineLayout;

    public ComputePipeline(VkCtx vkCtx, CompPipelineBuildInfo buildInfo) {
        ...
    }
    ...
}
```

The constructor receives a reference to the pipeline cache and to the `CompPipelineBuildInfo` class which contains the required information to create the pipeline. The `CompPipelineBuildInfo` is a record, of the `ComputePipeline` class which contains references to the shader module used for this compute pipeline, the layouts of the descriptor used in that shader and push constants size.

```java
package org.vulkanb.eng.graph.vk;

public record CompPipelineBuildInfo(ShaderModule shaderModule, DescSetLayout[] descSetLayouts, int pushConstantsSize) {
}
```

Going back to the `ComputePipeline` constructor, we first initialize the `VkPipelineShaderStageCreateInfo` structure with the compute shader information. In this specific case, we receive a  single `ShaderModule` instance through the `CompPipelineBuildInfo` record. We will use just one computer shader, so we just need a single shader module. As in
other types of shaders, compute shaders can use specialization constants, so we need to add support for them if `ShaderModule` instance defines them.
```java
public class ComputePipeline {
    ...
    public ComputePipeline(VkCtx vkCtx, CompPipelineBuildInfo buildInfo) {
        Logger.debug("Creating compute pipeline");
        Device device = vkCtx.getDevice();

        try (var stack = MemoryStack.stackPush()) {
            LongBuffer lp = stack.callocLong(1);
            ByteBuffer main = stack.UTF8("main");

            ShaderModule shaderModule = buildInfo.shaderModule();
            var shaderStage = VkPipelineShaderStageCreateInfo.calloc(stack)
                    .sType$Default()
                    .stage(shaderModule.getShaderStage())
                    .module(shaderModule.getHandle())
                    .pName(main);
            if (shaderModule.getSpecInfo() != null) {
                shaderStage.pSpecializationInfo(shaderModule.getSpecInfo());
            }
            ...
        }
        ...
    }
    ...
}
```

After that, if push constants size is greater than `0` we will create a push constant range that can be used in the `VK_SHADER_STAGE_COMPUTE_BIT` stage. We will use also
the descriptors layout information stored in the `CompPipelineBuildInfo` record to properly setup the `VkPipelineLayoutCreateInfo` structure, which allows us to create the pipeline layout.
```java
public class ComputePipeline {
    ...
    public ComputePipeline(VkCtx vkCtx, CompPipelineBuildInfo buildInfo) {
        ...
            VkPushConstantRange.Buffer vpcr = null;
            if (buildInfo.pushConstantsSize() > 0) {
                vpcr = VkPushConstantRange.calloc(1, stack)
                        .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                        .offset(0)
                        .size(buildInfo.pushConstantsSize());
            }
            DescSetLayout[] descSetLayouts = buildInfo.descSetLayouts();
            int numLayouts = descSetLayouts != null ? descSetLayouts.length : 0;
            LongBuffer ppLayout = stack.mallocLong(numLayouts);
            for (int i = 0; i < numLayouts; i++) {
                ppLayout.put(i, descSetLayouts[i].getVkDescLayout());
            }
            var pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pSetLayouts(ppLayout)
                    .pPushConstantRanges(vpcr);
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
    public ComputePipeline(VkCtx vkCtx, CompPipelineBuildInfo buildInfo) {
        ...
            var computePipelineCreateInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .stage(shaderStage)
                    .layout(vkPipelineLayout);
            vkCheck(vkCreateComputePipelines(device.getVkDevice(), vkCtx.getPipelineCache().getVkPipelineCache(),
                    computePipelineCreateInfo, null, lp), "Error creating compute pipeline");
            vkPipeline = lp.get(0);
        }
    }
    ...
}        
``` 

The `ComputePipeline` class is completed by the *getter* methods to retrieve the pipeline and pipeline layout handles and a `cleanup` method to release the resources.
```java
public class ComputePipeline {
    ...
    public void cleanup(VkCtx vkCtx) {
        Logger.debug("Destroying compute pipeline");
        vkDestroyPipelineLayout(vkCtx.getDevice().getVkDevice(), vkPipelineLayout, null);
        vkDestroyPipeline(vkCtx.getDevice().getVkDevice(), vkPipeline, null);
    }

    public long getVkPipeline() {
        return vkPipeline;
    }

    public long getVkPipelineLayout() {
        return vkPipelineLayout;
    }
}
```

The next step is to update the `Queue` class to support the creation of queues that belong to a family that allow the submission of compute commands. In order to do that, we will create a new inner class named `ComputeQueue`, that will create compute queues, and which is defined like this:
```java
public class Queue {
    ...
    public static class ComputeQueue extends Queue {

        public ComputeQueue(VkCtx vkCtx, int queueIndex) {
            super(vkCtx, getComputeQueueFamilyIndex(vkCtx), queueIndex);
        }

        private static int getComputeQueueFamilyIndex(VkCtx vkCtx) {
            int index = -1;
            var queuePropsBuff = vkCtx.getPhysDevice().getVkQueueFamilyProps();
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

Barriers are a way to split commands execution into two parts, the first part controls what is needed to be executed before the barrier and the second one what gets executed after the barrier. Memory barriers are submitted, as in the case of image barriers, using the `vkCmdPipelineBarrier2` function. 

The function ` vkCmdPipelineBarrier` function requires to specify, essentially, two parameters:
- `srcStageMask`: This refers to the pipeline stage that we are waiting to complete.
- `dstStageMask`: This refers to the pipeline stage which should not start after all the work affected by the conditions specified for the first part of the barrier is completed.

Memory barriers are defined by two parameters, `srcAccessMask` and `dstAccessMask`, which in combination with the parameters described above, provoke the following to be executed in order:
- All the commands submitted prior to the barrier must complete the stage specified by `srcStageMask`.
- All memory writes performed in combination of `srcStageMask` and `srcAccessMask` must be available (the data is written into the memory).
- The memory is visible (the caches are invalidated so they can pull the modified data) to any combination of ` dstStageMask` and ` dstAccessMask`.
- All the commands submitted after the barrier, which were blocked in the `dstStageMask` can now execute.

We will create a new method in the `VkUtil` class named `memoryBarrier` to set up memory barriers:
```java
public class VkUtil {
    ...
    public static void memoryBarrier(CmdBuffer cmdBuffer, int srcStageMask, int dstStageMask, int srcAccessMask,
                                     int dstAccessMask, int dependencyFlags) {
        try (var stack = MemoryStack.stackPush()) {
            VkMemoryBarrier2.Buffer buff = VkMemoryBarrier2.calloc(1, stack)
                    .sType$Default()
                    .srcStageMask(srcStageMask)
                    .dstStageMask(dstStageMask)
                    .srcAccessMask(srcAccessMask)
                    .dstAccessMask(dstAccessMask);

            VkDependencyInfo depInfo = VkDependencyInfo.calloc(stack)
                    .sType$Default()
                    .pMemoryBarriers(buff)
                    .dependencyFlags(dependencyFlags);

            vkCmdPipelineBarrier2(cmdBuffer.getVkCommandBuffer(), depInfo);
        }
    }
    ...
}
```

We have now all the pieces required to perform the animation using a compute shader, so we will create a new class, named `AnimRender` to put them into play. The structure of this class is similar to the equivalent classes for the scene or lighting stages. It starts like this:
```java
package org.vulkanb.eng.graph.anim;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.*;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.nio.LongBuffer;
import java.util.*;

import static org.lwjgl.vulkan.VK13.*;

public class AnimRender {

    private static final String COMPUTE_SHADER_FILE_GLSL = "resources/shaders/anim_comp.glsl";
    private static final String COMPUTE_SHADER_FILE_SPV = COMPUTE_SHADER_FILE_GLSL + ".spv";
    private static final int LOCAL_SIZE_X = 32;

    private final CmdBuffer cmdBuffer;
    private final CmdPool cmdPool;
    private final Queue.ComputeQueue computeQueue;
    private final Fence fence;
    private final Map<String, Integer> grpSizeMap;
    private final ComputePipeline pipeline;
    private final DescSetLayout stDescSetLayout;

    public AnimRender(VkCtx vkCtx) {
        fence = new Fence(vkCtx, true);
        computeQueue = new Queue.ComputeQueue(vkCtx, 0);

        cmdPool = new CmdPool(vkCtx, computeQueue.getQueueFamilyIndex(), false);
        cmdBuffer = new CmdBuffer(vkCtx, cmdPool, true, true);

        stDescSetLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                0, 1, VK_SHADER_STAGE_COMPUTE_BIT));

        ShaderModule shaderModule = createShaderModule(vkCtx);
        CompPipelineBuildInfo buildInfo = new CompPipelineBuildInfo(shaderModule, new DescSetLayout[]{
                stDescSetLayout, stDescSetLayout, stDescSetLayout, stDescSetLayout}, 0);
        pipeline = new ComputePipeline(vkCtx, buildInfo);
        shaderModule.cleanup(vkCtx);
        grpSizeMap = new HashMap<>();
    }

    private static ShaderModule createShaderModule(VkCtx vkCtx) {
        if (EngCfg.getInstance().isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(COMPUTE_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_compute_shader);
        }
        return new ShaderModule(vkCtx, VK_SHADER_STAGE_COMPUTE_BIT, COMPUTE_SHADER_FILE_SPV, null);
    }

    public void cleanup(VkCtx vkCtx) {
        pipeline.cleanup(vkCtx);
        stDescSetLayout.cleanup(vkCtx);
        fence.cleanup(vkCtx);
        cmdBuffer.cleanup(vkCtx, cmdPool);
        cmdPool.cleanup(vkCtx);
    }
    ...    
}
```

We will create an instance of a computation queue, create the layout of the descriptor sets that we will use in the compute shader, the shader module an create the compute
pipeline. We will also create a command buffer to submit computation tasks.

We now will create a new method method that should be called when new models are loaded. That method, named `loadModels`, is defined like this:
```java
public class AnimRender {
    ...
    public void loadModels(VkCtx vkCtx, ModelsCache modelsCache, List<Entity> entities, AnimationsCache animationsCache) {
        DescAllocator descAllocator = vkCtx.getDescAllocator();
        Device device = vkCtx.getDevice();
        DescSetLayout.LayoutInfo layoutInfo = stDescSetLayout.getLayoutInfo();
        var models = modelsCache.getModelsMap().values();
        for (VulkanModel vulkanModel : models) {
            if (!vulkanModel.hasAnimations()) {
                continue;
            }
            String modelId = vulkanModel.getId();
            int animationIdx = 0;
            for (VulkanAnimation animation : vulkanModel.getVulkanAnimationList()) {
                int buffPos = 0;
                for (VkBuffer jointsMatricesBuffer : animation.frameBufferList()) {
                    String id = modelId + "_" + animationIdx + "_" + buffPos;
                    DescSet descSet = descAllocator.addDescSet(device, id, stDescSetLayout);
                    descSet.setBuffer(device, jointsMatricesBuffer, jointsMatricesBuffer.getRequestedSize(), 0,
                            layoutInfo.descType());
                    buffPos++;
                }
                animationIdx++;
            }

            for (VulkanMesh mesh : vulkanModel.getVulkanMeshList()) {
                int vertexSize = 14 * VkUtils.FLOAT_SIZE;
                int groupSize = (int) Math.ceil(((float) mesh.verticesBuffer().getRequestedSize() / vertexSize) /
                        LOCAL_SIZE_X);
                DescSet vtxDescSet = descAllocator.addDescSet(device, mesh.id() + "_VTX", stDescSetLayout);
                vtxDescSet.setBuffer(device, mesh.verticesBuffer(), mesh.verticesBuffer().getRequestedSize(), 0,
                        layoutInfo.descType());
                grpSizeMap.put(mesh.id(), groupSize);

                DescSet weightsDescSet = descAllocator.addDescSet(device, mesh.id() + "_W", stDescSetLayout);
                weightsDescSet.setBuffer(device, mesh.weightsBuffer(), mesh.weightsBuffer().getRequestedSize(), 0,
                        layoutInfo.descType());
            }
        }

        int numEntities = entities.size();
        for (int i = 0; i < numEntities; i++) {
            var entity = entities.get(i);
            VulkanModel model = modelsCache.getModel(entity.getModelId());
            if (!model.hasAnimations()) {
                continue;
            }
            List<VulkanMesh> vulkanMeshList = model.getVulkanMeshList();
            int numMeshes = vulkanMeshList.size();
            for (int j = 0; j < numMeshes; j++) {
                var vulkanMesh = vulkanMeshList.get(j);
                VkBuffer animationBuffer = animationsCache.getBuffer(entity.getId(), vulkanMesh.id());
                DescSet descSet = descAllocator.addDescSet(device, entity.getId() + "_" + vulkanMesh.id() + "_ENT", stDescSetLayout);
                descSet.setBuffer(device, animationBuffer, animationBuffer.getRequestedSize(), 0, layoutInfo.descType());
            }
        }
    }
    ...
}
```
In this method, we first discard the models that do not contain animations. For each of the models that contain animations, we create a descriptor set that will hold an array of matrices with the transformation matrices associated to the joints of the model. Those matrices change for each animation frame, so for a model, we will have as many arrays (and therefore as many descriptors) as animation frames the model has. For each mesh of the model we will need at least, two storage buffers, the first one will hold the data for the bind position (vertices). The second storage buffer will contain the weights associated to each vertex (a vertex will have 4 weights that will modulate the bind position using the joint transformation matrices. Each of those weights will be associated to a joint index). Therefore we need to create two storage descriptor sets per mesh. Finally, we store the joint matrices descriptor sets and the storage descriptor sets in a map using the model identifier as the key. This will be used later on when rendering. 
As it has been mentioned several times before, while animating, we need to dump the results of the animation to a buffer. That data needs to be unique per entity associated to an animation model (the entities may start animations at different stages or at a different pace). We need to create descriptor sets for each of these buffers in the final part
of the method.

It is turn now to present the `render` method which will be responsible of recording the dispatching commands that will be executed through the compute shader pipeline to calculate the animations. 
```java
public class AnimRender {
    ...
    public void render(EngCtx engCtx, VkCtx vkCtx, ModelsCache modelsCache) {
        fence.fenceWait(vkCtx);
        fence.reset(vkCtx);

        try (var stack = MemoryStack.stackPush()) {
            recordingStart(vkCtx);

            VkUtils.memoryBarrier(cmdBuffer, VK_PIPELINE_STAGE_VERTEX_INPUT_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    0, VK_ACCESS_SHADER_WRITE_BIT, 0);

            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.getVkPipeline());

            LongBuffer descriptorSets = stack.mallocLong(4);

            Scene scene = engCtx.scene();
            DescAllocator descAllocator = vkCtx.getDescAllocator();

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
                List<VulkanMesh> vulkanMeshList = model.getVulkanMeshList();
                int numMeshes = vulkanMeshList.size();
                for (int j = 0; j < numMeshes; j++) {
                    var vulkanMesh = vulkanMeshList.get(j);
                    descriptorSets.put(0, descAllocator.getDescSet(vulkanMesh.id() + "_VTX").getVkDescriptorSet());
                    descriptorSets.put(1, descAllocator.getDescSet(vulkanMesh.id() + "_W").getVkDescriptorSet());
                    descriptorSets.put(2, descAllocator.getDescSet(entity.getId() + "_" + vulkanMesh.id() + "_ENT").getVkDescriptorSet());

                    String id = modelId + "_" + entityAnimation.getAnimationIdx() + "_" + entityAnimation.getCurrentFrame();
                    descriptorSets.put(3, descAllocator.getDescSet(id).getVkDescriptorSet());

                    vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_COMPUTE,
                            pipeline.getVkPipelineLayout(), 0, descriptorSets, null);

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
}
```

The code is similar to the recording methods in the scene, shadow and lighting phases. We first wait for the fence to prevent using the command while in use. Once we start the recording we first submit the global memory barrier, waiting for the vertex stage to complete before starting commands that will go through the compute stage. After that, we iterate over the models and their meshes, setting the appropriate descriptor sets that will hold the binding pose data and the weights list. For each associated entity we set up the descriptor linked to the storage buffer that will hold the results, and the joint matrices list associated to the specific frame used to render the entity. Finally we call the `vkCmdDispatch` function to dispatch the compute shader execution.

Finally, the `AnimRender` class defines some utility methods to start anf finish command recording.
```java
public class AnimRender {
    ...
    private void recordingStart(VkCtx vkCtx) {
        cmdPool.reset(vkCtx);
        cmdBuffer.beginRecording();
    }

    private void recordingStop() {
        cmdBuffer.endRecording();
    }
    ...
}
```

The next step is to write the compute shader which performs the calculations. The computer shader (`anim_comp.glsl`) starts like this:
```glsl
#version 450

layout (std430, set=0, binding=0) readonly buffer srcBuf {
    float data[];
} srcVector;

layout (std430, set=1, binding=0) readonly buffer weightsBuf {
    float data[];
} weightsVector;

layout (std430, set=2, binding=0) buffer dstBuf {
    float data[];
} dstVector;

layout (std430, set=3, binding=0) readonly buffer jointBuf {
    mat4 data[];
} jointMatrices;

layout (local_size_x=32, local_size_y=1, local_size_z=1) in;
...
```

The `srcVector` is the storage buffer that contains binding pose data (positions, texture coordinates, normals, bitangents and tangents). It is a readonly buffer since we will not writing to it. The `weightsVector` is also a readonly buffer that contains the weights associated to each vertex. The `dstVector` is the storage buffer that will hold our results, it will contain the positions, texture coordinates, normals, bitangents and tangents transformed according to the animation. Finally, the `jointMatrices` storage buffer, holds the list of transformation matrices applicable to each joint for a specific frame. Going back to the shaders, the `main` method starts like this:
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
    weights.x * jointMatrices.data[joints.x] * position +
    weights.y * jointMatrices.data[joints.y] * position +
    weights.z * jointMatrices.data[joints.z] * position +
    weights.w * jointMatrices.data[joints.w] * position;
    dstVector.data[baseIdxSrcBuf] = position.x / position.w;
    dstVector.data[baseIdxSrcBuf + 1] = position.y / position.w;
    dstVector.data[baseIdxSrcBuf + 2] = position.z / position.w;
    ...
}
```

After that, we get the vertex positions form the storage buffer that contains the vertex data for the bind position. That buffer can be split into slices of 14 floats: 3 floats for vertex positions, 3 for normal coordinates, 3 for tangent coordinates, 3 for bitangent coordinates and 2 for texture coordinates. Once we get the vertex position, we modify those coordinates by applying a modulation factor which is derived from multiplying the weight factor by the joint transformation matrix of the associated matrix.

The same process is applied to the normal, tangent and bitangent.
```glsl
void main()
{
    ...
    mat3 matJoint1 = mat3(transpose(inverse(jointMatrices.data[joints.x])));
    mat3 matJoint2 = mat3(transpose(inverse(jointMatrices.data[joints.y])));
    mat3 matJoint3 = mat3(transpose(inverse(jointMatrices.data[joints.z])));
    baseIdxSrcBuf += 3;
    vec3 normal = vec3(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1], srcVector.data[baseIdxSrcBuf + 2]);
    normal =
    weights.x * matJoint1 * normal +
    weights.y * matJoint2 * normal +
    weights.z * matJoint3 * normal;
    normal = normalize(normal);
    dstVector.data[baseIdxSrcBuf] = normal.x;
    dstVector.data[baseIdxSrcBuf + 1] = normal.y;
    dstVector.data[baseIdxSrcBuf + 2] = normal.z;

    baseIdxSrcBuf += 3;
    vec3 tangent = vec3(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1], srcVector.data[baseIdxSrcBuf + 2]);
    tangent =
    weights.x * matJoint1 * tangent +
    weights.y * matJoint2 * tangent +
    weights.z * matJoint3 * tangent;
    tangent = normalize(tangent);
    dstVector.data[baseIdxSrcBuf] = tangent.x;
    dstVector.data[baseIdxSrcBuf + 1] = tangent.y;
    dstVector.data[baseIdxSrcBuf + 2] = tangent.z;

    baseIdxSrcBuf += 3;
    vec3 bitangent = vec3(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1], srcVector.data[baseIdxSrcBuf + 2]);
    bitangent =
    weights.x * matJoint1 * bitangent +
    weights.y * matJoint2 * bitangent +
    weights.z * matJoint3 * bitangent;
    bitangent = normalize(bitangent);
    dstVector.data[baseIdxSrcBuf] = bitangent.x;
    dstVector.data[baseIdxSrcBuf + 1] = bitangent.y;
    dstVector.data[baseIdxSrcBuf + 2] = bitangent.z;
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

## Updates on scene rendering

Let us review now the changes in the scene render stage. In the `render` method we will need to access the proper buffers for animated entities. Remember that each
animated entity will have dedicated buffers per mesh where the model vertices will be transformed:
```java
public class ScnRender {
    ...
    public void render(EngCtx engCtx, VkCtx vkCtx, CmdBuffer cmdBuffer, ModelsCache modelsCache,
                       MaterialsCache materialsCache, AnimationsCache animationsCache, int currentFrame) {
        ...
            renderEntities(engCtx, cmdHandle, modelsCache, materialsCache, false);
            renderEntities(engCtx, cmdHandle, modelsCache, materialsCache, true);
        ...
    }

    private void renderEntities(EngCtx engCtx, VkCommandBuffer cmdHandle, ModelsCache modelsCache,
                                MaterialsCache materialsCache, boolean transparent) {
        ...
                    if (vulkanMaterial.isTransparent() == transparent) {
                        setPushConstants(cmdHandle, entity.getModelMatrix(), materialIdx);

                        var vtxBuffer = model.hasAnimations() ?
                                animationsCache.getBuffer(entity.getId(), vulkanMesh.id()) :
                                vulkanMesh.verticesBuffer();

                        vertexBuffer.put(0, vtxBuffer.getBuffer());

                        vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                        vkCmdBindIndexBuffer(cmdHandle, vulkanMesh.indicesBuffer().getBuffer(), 0, VK_INDEX_TYPE_UINT32);
                        vkCmdDrawIndexed(cmdHandle, vulkanMesh.numIndices(), 1, 0, 0, 0);
                    }
        ...
    }
    ...
}
``` 

We just need to check if the entity is related to a model that has animations or not. If so, instead of using the data associated to the meshes of the model, we use the buffer associated to the animation for that entity.

## Updates on shadow rendering

We need also to update the code that renders shadow cascades. The changes are quite similar than in the geometry phase, we just need to select the buffer that holds the vertices transformed according to the data of a specific key frame in the `ShadowRender` class.

```java
public class ShadowRender {
    ...
    public void render(EngCtx engCtx, VkCtx vkCtx, CmdBuffer cmdBuffer, ModelsCache modelsCache,
                       MaterialsCache materialsCache, int currentFrame) {
        ...
                    setPushConstants(cmdHandle, entity.getModelMatrix(), materialIdx);

                    var vtxBuffer = model.hasAnimations() ?
                            animationsCache.getBuffer(entity.getId(), vulkanMesh.id()) :
                            vulkanMesh.verticesBuffer();

                    vertexBuffer.put(0, vtxBuffer.getBuffer());
        ...
    }
    ...
}
```

As in the scene render stage, the shaders do not need to be changed.

## Final updates

We have almost finished with the changes required in the code base to use animations. The next step is to modify the `Render` class to use the `AnimRender` class and the
`AnimationsCache`.
```java
public class Render {

    private final AnimRender animRender;
    private final AnimationsCache animationsCache;
    ...
    public Render(EngCtx engCtx) {
        ...
        animRender = new AnimRender(vkCtx);
        ...
        animationComputeActivity = new AnimationComputeActivity(commandPool, pipelineCache, scene);
    }

    public void cleanup() {
        ...
        animRender.cleanup(vkCtx);
        ...
        animationsCache.cleanup(vkCtx);
        ...
    }

    public void init(EngCtx engCtx, InitData initData) {
        ...
        animationsCache.loadAnimations(vkCtx, engCtx.scene().getEntities(), modelsCache);
        animRender.loadModels(vkCtx, modelsCache, engCtx.scene().getEntities(), animationsCache);
    }
    ...
    public void render(EngCtx engCtx) {
        SwapChain swapChain = vkCtx.getSwapChain();

        waitForFence(currentFrame);

        var cmdPool = cmdPools[currentFrame];
        var cmdBuffer = cmdBuffers[currentFrame];

        animRender.render(engCtx, vkCtx, modelsCache);

        recordingStart(cmdPool, cmdBuffer);

        scnRender.render(engCtx, vkCtx, cmdBuffer, modelsCache, materialsCache, animationsCache, currentFrame);
        shadowRender.render(engCtx, vkCtx, cmdBuffer, modelsCache, materialsCache, animationsCache, currentFrame);
        lightRender.render(engCtx, vkCtx, cmdBuffer, scnRender.getMrtAttachments(), shadowRender.getShadowAttachment(),
                shadowRender.getCascadeShadows(currentFrame), currentFrame);
        postRender.render(vkCtx, cmdBuffer, lightRender.getAttachment());
        guiRender.render(vkCtx, cmdBuffer, currentFrame, postRender.getAttachment());

        int imageIndex;
        if (resize || (imageIndex = swapChain.acquireNextImage(vkCtx.getDevice(), presCompleteSemphs[currentFrame])) < 0) {
            resize(engCtx);
            return;
        }

        swapChainRender.render(vkCtx, cmdBuffer, postRender.getAttachment(), imageIndex);

        recordingStop(cmdBuffer);

        submit(cmdBuffer, currentFrame, imageIndex);

        resize = swapChain.presentImage(presentQueue, renderCompleteSemphs[imageIndex], imageIndex);

        currentFrame = (currentFrame + 1) % VkUtils.MAX_IN_FLIGHT;
    }
    ...
}
```

We need to update also the `Engine` class due to the change in the `Render` class constructor:

```java
public class Engine {
    ...
    public Engine(String windowTitle, IGameLogic appLogic) {
        ...
        render.init(engCtx, initData);
    }
    ...
}
```

The last step is to load an animated model in the `Main` class. We just need to load a new animated model and associate it to an entity. We will update the animation 
frame in each invocation of the `update` method. We will also set space bar to start / resume the animation
```java
public class Main implements IGameLogic {
    ...
    private Entity bobEntity;
    ...
    private int maxFrames;
    ...
    @Override
    public InitData init(EngCtx engCtx) {
        ...
        ModelData bobModelData = ModelLoader.loadModel("resources/models/bob/boblamp.json");
        models.add(bobModelData);
        maxFrames = bobModelData.animations().get(0).frames().size();
        bobEntity = new Entity("BobEntity", bobModelData.id(), new Vector3f(0.0f, 0.0f, 0.0f));
        bobEntity.setScale(0.04f);
        bobEntity.getRotation().rotateY((float) Math.toRadians(-90.0f));
        bobEntity.updateModelMatrix();
        bobEntity.setEntityAnimation(new EntityAnimation(true, 0, 0));
        scene.addEntity(bobEntity);

        List<MaterialData> materials = new ArrayList<>();
        materials.addAll(ModelLoader.loadMaterials("resources/models/sponza/Sponza_mat.json"));
        materials.addAll(ModelLoader.loadMaterials("resources/models/bob/boblamp_mat.json"));
        ...
        camera.setPosition(-5.0f, 3.0f, 0.0f);
        ...
    }
    ...
    @Override
    public void input(EngCtx engCtx, long diffTimeMillis) {
        ...
        if (ki.keySinglePress(GLFW_KEY_SPACE)) {
            EntityAnimation entityAnimation = bobEntity.getEntityAnimation();
            entityAnimation.setStarted(!entityAnimation.isStarted());
        }
        ...
    }
    ...
    @Override
    public void update(EngCtx engCtx, long diffTimeMillis) {
        EntityAnimation entityAnimation = bobEntity.getEntityAnimation();
        if (entityAnimation.isStarted()) {
            int currentFrame = Math.floorMod(entityAnimation.getCurrentFrame() + 1, maxFrames);
            entityAnimation.setCurrentFrame(currentFrame);
        }
    }
    ...    
}
```

We are now done with the changes, you should now be able to see the scene with shadows applied, as in the following screenshot:

<img src="rc17-screen-shot.png" title="" alt="Screen Shot" data-align="center">

[Next chapter](../chapter-18/chapter-18.md)