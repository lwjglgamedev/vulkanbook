package org.vulkanb.eng.model;

import com.beust.jcommander.*;
import com.google.gson.*;
import org.joml.Vector4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryStack;
import org.tinylog.Logger;
import org.vulkanb.eng.graph.vk.VkUtils;

import java.io.*;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

import static org.lwjgl.assimp.Assimp.*;

public class ModelGenerator {

    private static final Pattern EMBED_TEXT_ID = Pattern.compile("\\*([0-9]+)");
    private static final int FLAGS = aiProcess_GenSmoothNormals | aiProcess_JoinIdenticalVertices |
            aiProcess_Triangulate | aiProcess_FixInfacingNormals | aiProcess_CalcTangentSpace |
            aiProcess_PreTransformVertices;

    @Parameter(names = "-m", description = "Model path", required = true)
    private String modelPath;

    public static void main(String[] args) {
        var main = new ModelGenerator();
        var jCmd = JCommander.newBuilder().addObject(main).build();
        try {
            jCmd.parse(args);
            main.mainProcessing();
        } catch (ParameterException excp) {
            jCmd.usage();
        } catch (IOException excp) {
            Logger.error("Error generating model", excp);
        }
    }

    private static List<Float> processBitangents(AIMesh aiMesh, List<Float> normals) {
        List<Float> biTangents = new ArrayList<>();
        AIVector3D.Buffer aiBitangents = aiMesh.mBitangents();
        while (aiBitangents != null && aiBitangents.remaining() > 0) {
            AIVector3D aiBitangent = aiBitangents.get();
            biTangents.add(aiBitangent.x());
            biTangents.add(aiBitangent.y());
            biTangents.add(aiBitangent.z());
        }

        // Assimp may not calculate tangents with models that do not have texture coordinates. Just create empty values
        if (biTangents.isEmpty()) {
            biTangents = new ArrayList<>(Collections.nCopies(normals.size(), 0.0f));
        }
        return biTangents;
    }

    private static List<Float> processNormals(AIMesh aiMesh) {
        List<Float> normals = new ArrayList<>();

        AIVector3D.Buffer aiNormals = aiMesh.mNormals();
        while (aiNormals != null && aiNormals.remaining() > 0) {
            AIVector3D aiNormal = aiNormals.get();
            normals.add(aiNormal.x());
            normals.add(aiNormal.y());
            normals.add(aiNormal.z());
        }
        return normals;
    }

    private static List<Float> processTangents(AIMesh aiMesh, List<Float> normals) {
        List<Float> tangents = new ArrayList<>();
        AIVector3D.Buffer aiTangents = aiMesh.mTangents();
        while (aiTangents != null && aiTangents.remaining() > 0) {
            AIVector3D aiTangent = aiTangents.get();
            tangents.add(aiTangent.x());
            tangents.add(aiTangent.y());
            tangents.add(aiTangent.z());
        }

        // Assimp may not calculate tangents with models that do not have texture coordinates. Just create empty values
        if (tangents.isEmpty()) {
            tangents = new ArrayList<>(Collections.nCopies(normals.size(), 0.0f));
        }
        return tangents;
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

    private void mainProcessing() throws IOException {
        Logger.debug("Loading model data [{}]", modelPath);
        var modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            throw new RuntimeException("Model path does not exist [" + modelPath + "]");
        }

        AIScene aiScene = aiImportFile(modelPath, FLAGS);
        if (aiScene == null) {
            throw new RuntimeException("Error loading model [modelPath: " + modelPath + "]");
        }

        String modelId = modelFile.getName();
        if (modelId.contains(".")) {
            modelId = modelId.substring(0, modelId.lastIndexOf('.'));
        }

        ModelBinData modelBinData = new ModelBinData(modelPath);

        int numMaterials = aiScene.mNumMaterials();
        Logger.debug("Number of materials: {}", numMaterials);
        List<MaterialData> matList = new ArrayList<>();
        File parentDirectory = modelFile.getParentFile();
        for (int i = 0; i < numMaterials; i++) {
            var aiMaterial = AIMaterial.create(aiScene.mMaterials().get(i));
            MaterialData material = processMaterial(aiScene, aiMaterial, modelId, parentDirectory.getPath(), i);
            matList.add(material);
        }

        int numMeshes = aiScene.mNumMeshes();
        PointerBuffer aiMeshes = aiScene.mMeshes();
        List<MeshData> meshList = new ArrayList<>();
        for (int i = 0; i < numMeshes; i++) {
            AIMesh aiMesh = AIMesh.create(aiMeshes.get(i));
            MeshData meshData = processMesh(aiMesh, matList, i, modelBinData);
            meshList.add(meshData);
        }

        var model = new ModelData(modelId, meshList, modelBinData.getVtxFilePath(), modelBinData.getIdxFilePath());

        String outModelFile = modelPath.substring(0, modelPath.lastIndexOf('.')) + ".json";
        Writer writer = new FileWriter(outModelFile);
        var gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).setPrettyPrinting().create();
        gson.toJson(model, writer);
        writer.flush();
        writer.close();
        Logger.info("Generated model file [{}]", outModelFile);

        String outMaterialFile = modelPath.substring(0, modelPath.lastIndexOf('.')) + "_mat.json";
        writer = new FileWriter(outMaterialFile);
        gson.toJson(matList, writer);
        writer.flush();
        writer.close();
        Logger.info("Generated materials file [{}]", outMaterialFile);

        modelBinData.close();
        Logger.info("Generated vtx file [{}]", modelBinData.getVtxFilePath());
        Logger.info("Generated idx file [{}]", modelBinData.getIdxFilePath());
    }

    private List<Integer> processIndices(AIMesh aiMesh) {
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

    private MaterialData processMaterial(AIScene aiScene, AIMaterial aiMaterial, String modelName, String baseDir, int pos)
            throws IOException {
        Vector4f diffuse = new Vector4f();
        AIColor4D color = AIColor4D.create();

        int result = aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0,
                color);
        if (result == aiReturn_SUCCESS) {
            diffuse.set(color.r(), color.g(), color.b(), color.a());
        }

        String diffuseTexture = processTexture(aiScene, aiMaterial, baseDir, aiTextureType_DIFFUSE);
        String normalTexture = processTexture(aiScene, aiMaterial, baseDir, aiTextureType_NORMALS);
        String metallicRoughTexture = processTexture(aiScene, aiMaterial, baseDir, AI_MATKEY_GLTF_PBRMETALLICROUGHNESS_METALLICROUGHNESS_TEXTURE);

        float[] metallicArr = new float[]{0.0f};
        int[] pMax = new int[]{1};
        result = aiGetMaterialFloatArray(aiMaterial, AI_MATKEY_METALLIC_FACTOR, aiTextureType_NONE, 0, metallicArr, pMax);
        if (result != aiReturn_SUCCESS) {
            metallicArr[0] = 0.0f;
        }

        float[] roughnessArr = new float[]{0.0f};
        result = aiGetMaterialFloatArray(aiMaterial, AI_MATKEY_ROUGHNESS_FACTOR, aiTextureType_NONE, 0, roughnessArr, pMax);
        if (result != aiReturn_SUCCESS) {
            roughnessArr[0] = 1.0f;
        }

        return new MaterialData(modelName + "-mat-" + pos, diffuseTexture, normalTexture, metallicRoughTexture, diffuse,
                roughnessArr[0], metallicArr[0]);
    }

    private MeshData processMesh(AIMesh aiMesh, List<MaterialData> materialList, int meshPosition,
                                 ModelBinData modelBinData) throws IOException {
        List<Float> vertices = processVertices(aiMesh);
        List<Float> normals = processNormals(aiMesh);
        List<Float> tangents = processTangents(aiMesh, normals);
        List<Float> biTangents = processBitangents(aiMesh, normals);
        List<Float> textCoords = processTextCoords(aiMesh);
        List<Integer> indices = processIndices(aiMesh);

        int vtxSize = vertices.size();
        if (textCoords.isEmpty()) {
            textCoords = Collections.nCopies((vtxSize / 3) * 2, 0.0f);
        }

        DataOutputStream vtxOutput = modelBinData.getVtxOutput();
        int rows = vtxSize / 3;
        int vtxInc = (vtxSize + normals.size() + textCoords.size()) * VkUtils.FLOAT_SIZE;
        for (int row = 0; row < rows; row++) {
            int startPos = row * 3;
            int startTextCoord = row * 2;
            vtxOutput.writeFloat(vertices.get(startPos));
            vtxOutput.writeFloat(vertices.get(startPos + 1));
            vtxOutput.writeFloat(vertices.get(startPos + 2));
            vtxOutput.writeFloat(normals.get(startPos));
            vtxOutput.writeFloat(normals.get(startPos + 1));
            vtxOutput.writeFloat(normals.get(startPos + 2));
            vtxOutput.writeFloat(textCoords.get(startTextCoord));
            vtxOutput.writeFloat(textCoords.get(startTextCoord + 1));
        }

        DataOutputStream idxOutput = modelBinData.getIdxOutput();
        int idxSize = indices.size();
        int idxInc = idxSize * VkUtils.INT_SIZE;
        for (int idx = 0; idx < idxSize; idx++) {
            idxOutput.writeInt(indices.get(idx));
        }

        // Add position to mesh id to ensure unique ids
        String id = aiMesh.mName().dataString() + "_" + meshPosition;
        int materialIdx = aiMesh.mMaterialIndex();
        String materialId = "";
        if (materialIdx >= 0 && materialIdx < materialList.size()) {
            materialId = materialList.get(materialIdx).id();
        }

        var meshData = new MeshData(id, materialId, modelBinData.getVtxOffset(), vtxInc, modelBinData.getIdxOffset(),
                idxInc);

        modelBinData.incVtxOffset(vtxInc);
        modelBinData.incIdxOffset(idxInc);
        return meshData;
    }

    private String processTexture(AIScene aiScene, AIMaterial aiMaterial, String baseDir, int textureType) throws IOException {
        String texturePath;
        try (var stack = MemoryStack.stackPush()) {
            int numEmbeddedTextures = aiScene.mNumTextures();
            AIString aiTexturePath = AIString.calloc(stack);
            aiGetMaterialTexture(aiMaterial, textureType, 0, aiTexturePath, (IntBuffer) null,
                    null, null, null, null, null);
            texturePath = aiTexturePath.dataString();
            if (texturePath != null && !texturePath.isEmpty()) {
                Matcher matcher = EMBED_TEXT_ID.matcher(texturePath);
                int embeddedTextureIdx = matcher.matches() && matcher.groupCount() > 0 ? Integer.parseInt(matcher.group(1)) : -1;
                if (embeddedTextureIdx >= 0 && embeddedTextureIdx < numEmbeddedTextures) {
                    // Embedded texture
                    var aiTexture = AITexture.create(aiScene.mTextures().get(embeddedTextureIdx));
                    String baseFileName = aiTexture.mFilename().dataString() + ".png";
                    texturePath = baseDir + "/" + baseFileName;
                    Logger.info("Dumping texture file to [{}]", texturePath);
                    var channel = FileChannel.open(Path.of(texturePath), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    channel.write(aiTexture.pcDataCompressed());
                } else {
                    texturePath = baseDir + "/" + new File(texturePath).getName();
                }
            }
        }

        return texturePath;
    }

    private List<Float> processVertices(AIMesh aiMesh) {
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