package org.vulkanb.eng.model;

import java.io.*;

public class ModelBinData {

    private final String idxFilePath;
    private final DataOutputStream idxOutput;
    private final String vtxFilePath;
    private final DataOutputStream vtxOutput;
    private int idxOffset;
    private int vtxOffset;

    public ModelBinData(String modelPath) throws FileNotFoundException {
        vtxFilePath = modelPath.substring(0, modelPath.lastIndexOf('.')) + ".vtx";
        vtxOutput = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(vtxFilePath)));
        idxFilePath = modelPath.substring(0, modelPath.lastIndexOf('.')) + ".idx";
        idxOutput = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(idxFilePath)));
    }

    public void close() throws IOException {
        vtxOutput.close();
        idxOutput.close();
    }

    public String getIdxFilePath() {
        return idxFilePath;
    }

    public int getIdxOffset() {
        return idxOffset;
    }

    public DataOutputStream getIdxOutput() {
        return idxOutput;
    }

    public String getVtxFilePath() {
        return vtxFilePath;
    }

    public int getVtxOffset() {
        return vtxOffset;
    }

    public DataOutputStream getVtxOutput() {
        return vtxOutput;
    }

    public void incIdxOffset(int inc) {
        idxOffset += inc;
    }

    public void incVtxOffset(int inc) {
        vtxOffset += inc;
    }
}
