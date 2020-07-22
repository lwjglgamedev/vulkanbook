package org.vulkanb.eng.scene;

import org.apache.logging.log4j.*;
import org.joml.Vector4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryStack;

import java.io.File;
import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.assimp.Assimp.*;
import static org.vulkanb.eng.EngineUtils.*;

public class ModelLoader {

    private static final Logger LOGGER = LogManager.getLogger();

    private ModelLoader() {
        // Utility class
    }

    public static MeshData[] loadMeshes(String id, String modelPath, String texturesDir, int flags) {
        LOGGER.debug("Loading mesh data [{}]", modelPath);
        if (!new File(modelPath).exists()) {
            throw new RuntimeException("Model path does not exist [" + modelPath + "]");
        }
        if (!new File(texturesDir).exists()) {
            throw new RuntimeException("Textures path does not exist [" + texturesDir + "]");
        }

        AIScene aiScene = aiImportFile(modelPath, flags);
        if (aiScene == null) {
            throw new RuntimeException("Error loading model [modelPath: " + modelPath + ", texturesDir:" + texturesDir + "]");
        }

        int numMaterials = aiScene.mNumMaterials();
        List<Material> materials = new ArrayList<>();
        for (int i = 0; i < numMaterials; i++) {
            AIMaterial aiMaterial = AIMaterial.create(aiScene.mMaterials().get(i));
            processMaterial(aiMaterial, materials, texturesDir);
        }

        int numMeshes = aiScene.mNumMeshes();
        PointerBuffer aiMeshes = aiScene.mMeshes();
        MeshData[] meshesData = new MeshData[numMeshes];
        for (int i = 0; i < numMeshes; i++) {
            AIMesh aiMesh = AIMesh.create(aiMeshes.get(i));
            MeshData meshData = processMesh(id, aiMesh, materials);
            meshesData[i] = meshData;
        }

        aiReleaseImport(aiScene);
        LOGGER.debug("Loaded mesh data [{}]", modelPath);
        return meshesData;
    }

    public static MeshData[] loadMeshes(String id, String modelPath, String texturesDir) {
        return loadMeshes(id, modelPath, texturesDir, aiProcess_GenSmoothNormals | aiProcess_JoinIdenticalVertices |
                aiProcess_Triangulate | aiProcess_FixInfacingNormals | aiProcess_CalcTangentSpace);
    }

    protected static List<Integer> processIndices(AIMesh aiMesh) {
        List<Integer> indices = new ArrayList<>();
        int numFaces = aiMesh.mNumFaces();
        AIFace.Buffer aiFaces = aiMesh.mFaces();
        for (int i = 0; i < numFaces; i++) {
            AIFace aiFace = aiFaces.get(i);
            IntBuffer buffer = aiFace.mIndices();
            while (buffer.remaining() > 0) {
                indices.add(buffer.get());
            }
        }
        return indices;
    }

    private static void processMaterial(AIMaterial aiMaterial, List<Material> materials, String texturesDir) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIColor4D colour = AIColor4D.create();

            AIString aiTexturePath = AIString.callocStack(stack);
            aiGetMaterialTexture(aiMaterial, aiTextureType_DIFFUSE, 0, aiTexturePath, (IntBuffer) null,
                    null, null, null, null, null);
            String texturePath = aiTexturePath.dataString();
            if (texturePath != null && texturePath.length() > 0) {
                texturePath = texturesDir + File.separator + new File(texturePath).getName();
            }

            Vector4f diffuse = Material.DEFAULT_COLOR;
            int result = aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0,
                    colour);
            if (result == aiReturn_SUCCESS) {
                diffuse = new Vector4f(colour.r(), colour.g(), colour.b(), colour.a());
            }

            Material material = new Material(texturePath, diffuse);
            materials.add(material);
        }
    }

    private static MeshData processMesh(String id, AIMesh aiMesh, List<Material> materials) {
        List<Float> vertices = processVertices(aiMesh);
        List<Float> textCoords = processTextCoords(aiMesh);
        List<Integer> indices = processIndices(aiMesh);

        // Texture coordinates may not have been populated. We need at least the empty slots
        if (textCoords.isEmpty()) {
            int numElements = (vertices.size() / 3) * 2;
            for (int i = 0; i < numElements; i++) {
                textCoords.add(0.0f);
            }
        }

        Material material;
        int materialIdx = aiMesh.mMaterialIndex();
        if (materialIdx >= 0 && materialIdx < materials.size()) {
            material = materials.get(materialIdx);
        } else {
            material = new Material();
        }
        return new MeshData(id, listToArray(vertices), listToArray(textCoords), listIntToArray(indices),
                material);
    }

    private static List<Float> processTextCoords(AIMesh aiMesh) {
        List<Float> textCoords = new ArrayList<>();
        AIVector3D.Buffer aiTextCoords = aiMesh.mTextureCoords(0);
        int numTextCoords = aiTextCoords != null ? aiTextCoords.remaining() : 0;
        for (int i = 0; i < numTextCoords; i++) {
            AIVector3D textCoord = aiTextCoords.get();
            textCoords.add(textCoord.x());
            textCoords.add(1 - textCoord.y());
        }
        return textCoords;
    }

    private static List<Float> processVertices(AIMesh aiMesh) {
        List<Float> vertices = new ArrayList<>();
        AIVector3D.Buffer aiVertices = aiMesh.mVertices();
        while (aiVertices.remaining() > 0) {
            AIVector3D aiVertex = aiVertices.get();
            vertices.add(aiVertex.x());
            vertices.add(aiVertex.y());
            vertices.add(aiVertex.z());
        }
        return vertices;
    }
}
