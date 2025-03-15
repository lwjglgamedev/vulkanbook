package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.tinylog.Logger;

import java.io.*;
import java.nio.*;
import java.nio.file.Files;

import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class ShaderModule {

    private final long handle;
    private final int shaderStage;

    public ShaderModule(VkCtx vkCtx, int shaderStage, String shaderSpvFile) {
        try {
            byte[] moduleContents = Files.readAllBytes(new File(shaderSpvFile).toPath());
            handle = createShaderModule(vkCtx, moduleContents);
            this.shaderStage = shaderStage;
        } catch (IOException excp) {
            Logger.error("Error reading shader file", excp);
            throw new RuntimeException(excp);
        }
    }

    private static long createShaderModule(VkCtx vkCtx, byte[] code) {
        try (var stack = MemoryStack.stackPush()) {
            ByteBuffer pCode = stack.malloc(code.length).put(0, code);

            var moduleCreateInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default()
                    .pCode(pCode);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateShaderModule(vkCtx.getDevice().getVkDevice(), moduleCreateInfo, null, lp),
                    "Failed to create shader module");

            return lp.get(0);
        }
    }

    public void cleanup(VkCtx vkCtx) {
        vkDestroyShaderModule(vkCtx.getDevice().getVkDevice(), handle, null);
    }

    public long getHandle() {
        return handle;
    }

    public int getShaderStage() {
        return shaderStage;
    }
}