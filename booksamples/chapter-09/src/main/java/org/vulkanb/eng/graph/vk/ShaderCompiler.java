package org.vulkanb.eng.graph.vk;

import org.lwjgl.util.shaderc.Shaderc;
import org.tinylog.Logger;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;

public class ShaderCompiler {

    private ShaderCompiler() {
        // Utility class
    }

    public static byte[] compileShader(String shaderCode, int shaderType) {
        long compiler = 0;
        long options = 0;
        byte[] compiledShader;

        try {
            compiler = Shaderc.shaderc_compiler_initialize();
            options = Shaderc.shaderc_compile_options_initialize();

            long result = Shaderc.shaderc_compile_into_spv(
                    compiler,
                    shaderCode,
                    shaderType,
                    "shader.glsl",
                    "main",
                    options
            );

            if (Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success) {
                throw new RuntimeException("Shader compilation failed: " + Shaderc.shaderc_result_get_error_message(result));
            }

            ByteBuffer buffer = Shaderc.shaderc_result_get_bytes(result);
            compiledShader = new byte[buffer.remaining()];
            buffer.get(compiledShader);
        } finally {
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }

        return compiledShader;
    }

    public static void compileShaderIfChanged(String glsShaderFile, int shaderType) {
        byte[] compiledShader;
        try {
            File glslFile = new File(glsShaderFile);
            File spvFile = new File(glsShaderFile + ".spv");
            if (!spvFile.exists() || glslFile.lastModified() > spvFile.lastModified()) {
                Logger.debug("Compiling [{}] to [{}]", glslFile.getPath(), spvFile.getPath());
                String shaderCode = new String(Files.readAllBytes(glslFile.toPath()));

                compiledShader = compileShader(shaderCode, shaderType);
                Files.write(spvFile.toPath(), compiledShader);
            } else {
                Logger.debug("Shader [{}] already compiled. Loading compiled version: [{}]", glslFile.getPath(), spvFile.getPath());
            }
        } catch (IOException excp) {
            throw new RuntimeException(excp);
        }
    }
}
