package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.io.*;
import java.nio.*;
import java.nio.file.Files;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class ShaderProgram {
    private final Device device;
    private final ShaderModule[] shaderModules;

    public ShaderProgram(Device device, ShaderModuleData[] shaderModuleData) {
        try {
            this.device = device;
            int numModules = shaderModuleData != null ? shaderModuleData.length : 0;
            shaderModules = new ShaderModule[numModules];
            for (int i = 0; i < numModules; i++) {
                byte[] moduleContents = Files.readAllBytes(new File(shaderModuleData[i].shaderSpvFile()).toPath());
                long moduleHandle = createShaderModule(moduleContents);
                shaderModules[i] = new ShaderModule(shaderModuleData[i].shaderStage(), moduleHandle,
                        shaderModuleData[i].specInfo());
            }
        } catch (IOException excp) {
            Logger.error("Error reading shader files", excp);
            throw new RuntimeException(excp);
        }
    }

    public void cleanup() {
        for (ShaderModule shaderModule : shaderModules) {
            vkDestroyShaderModule(device.getVkDevice(), shaderModule.handle(), null);
        }
    }

    private long createShaderModule(byte[] code) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pCode = stack.malloc(code.length).put(0, code);

            VkShaderModuleCreateInfo moduleCreateInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(pCode);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateShaderModule(device.getVkDevice(), moduleCreateInfo, null, lp),
                    "Failed to create shader module");

            return lp.get(0);
        }
    }

    public ShaderModule[] getShaderModules() {
        return shaderModules;
    }

    public record ShaderModule(int shaderStage, long handle, VkSpecializationInfo specInfo) {
    }

    public record ShaderModuleData(int shaderStage, String shaderSpvFile, VkSpecializationInfo specInfo) {
        public ShaderModuleData(int shaderStage, String shaderSpvFile) {
            this(shaderStage, shaderSpvFile, null);
        }
    }
}
