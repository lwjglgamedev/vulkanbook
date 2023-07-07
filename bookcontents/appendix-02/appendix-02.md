# A sample 3D game

In this appendix we will provide a ample 3D game to show how everything fits together. I will not explain the whole set of source code, instead, I will just highlight some changes made to the core source code base and some remarks.

You can find the complete source code for this chapter [here](../../booksamples/appendix-02).

## Overview of the game

The game is inspired by a classic [Sokoban](https://es.wikipedia.org/wiki/Sokoban). In that game you were to push the boxes to its final place in several scenarios acting as puzzles. In this case you will play the rol of a knight which needs to push several boxes to the finish place. You can only push them, pulling is not allowed, therefore making a wrong move can block a maze.

Some remarks:
- The game allows to define multiple levels (using ASCII code to define the contents of each cell) and uses a JSON file to list them (you can make your own puzzles and contribute to the book through a pull request if you want).
- Therefore, in order to load JSON files we will use the [JSON-java](https://github.com/stleary/JSON-java) library (simple enough).
- We will need to tweak a little bit the GUI code to use custom fonts. AVailable fonts will be used defined through a JSON file.
- I think I've gave proper credit for the different models, sounds and fonts used. if there something missing please let me know.

## GUI Modification

In order to use custom fonts we will need to create specific textures for each font family. In order to control that process we will create a new class named `FontsManager` which is defined like this:

```java
package org.vulkanb.eng.graph.gui;

import imgui.*;
import imgui.type.ImInt;
import org.json.*;
import org.tinylog.Logger;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;

public class FontsManager {

    private static final String CONFIG_FILE = "resources/fonts/fonts.json";

    private final Map<String, ImFont> fontsMap;
    private ImFont defaultFont;
    private Texture fontsTexture;

    public FontsManager(CommandPool commandPool, Queue queue) {
        Logger.debug("Loading font configuration file {}", CONFIG_FILE);
        fontsMap = new HashMap<>();

        try {
            ImGuiIO imGuiIO = ImGui.getIO();
            defaultFont = imGuiIO.getFonts().addFontDefault();

            String cfgFileContents = Files.readString(Path.of(CONFIG_FILE));
            JSONObject jsonObj = new JSONObject(cfgFileContents);

            JSONArray arr = jsonObj.getJSONArray("fonts");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject jsonObject = arr.getJSONObject(i);
                String id = jsonObject.getString("id");
                String ttfFile = jsonObject.getString("ttfFile");
                float size = jsonObject.getFloat("size");

                ImFont font = imGuiIO.getFonts().addFontFromFileTTF(ttfFile, size);
                fontsMap.put(id, font);
            }
            imGuiIO.getFonts().build();

            ImInt texWidth = new ImInt();
            ImInt texHeight = new ImInt();
            ByteBuffer buf = imGuiIO.getFonts().getTexDataAsRGBA32(texWidth, texHeight);
            fontsTexture = new Texture(commandPool.getDevice(), buf, texWidth.get(), texHeight.get(), VK_FORMAT_R8G8B8A8_UNORM);

            CommandBuffer cmd = new CommandBuffer(commandPool, true, true);
            cmd.beginRecording();
            fontsTexture.recordTextureTransition(cmd);
            cmd.endRecording();
            cmd.submitAndWait(commandPool.getDevice(), queue);
            cmd.cleanup();
        } catch (IOException excp) {
            Logger.error("Error loading configuration file {}", CONFIG_FILE, excp);
        }
    }

    public void cleanup() {
        fontsTexture.cleanup();
    }

    public ImFont getDefaultFont() {
        return defaultFont;
    }

    public Texture getFontsTexture() {
        return fontsTexture;
    }

    public ImFont getFont(String fontId) {
        ImFont font;
        if (fontsMap.containsKey(fontId)) {
            font = fontsMap.get(fontId);
        } else {
            Logger.warn("Requested unknown font {}", fontId);
            font = defaultFont;
        }
        return font;
    }
}
```

As you can see, in the constructor we load the JSON file which defines the fonts available. That file may look like this:

```json
{
  "fonts": [
    {
      "id": "MAIN",
      "ttfFile": "resources/fonts/neuropolitical rg.otf",
      "size": 82.0
    },	
  ]
}
```

A font entry is defined by an identifier, which will be used later to use it, a path to a True Type Font (TTF) file and a size. Once loaded, we use the Imgui `addFontFromFileTTF` function to create an `ImFont` instance. We will store those instances in a map to later use it. After we have finished loading all the fonts we need to call the `build` function. After that step Imgui is able to generate a texture for all the fonts which we need to upload the GPU as usual (recording the texture transition through a command buffer). The class provides methods to retrieve a font instance using its identifier, to get the fonts texture and to cleanup the resources.

We will use the `FontsManager` class in the `GuiRenderActivity` class:

```java
public class GuiRenderActivity {
    ...
    private FontsManager fontsManager;
    ...
    public void cleanup() {
        ...
        fontsManager.cleanup();
    }
    ...
    private void createDescriptorSets() {
        ...
        textureDescriptorSet = new TextureDescriptorSet(descriptorPool, textureDescriptorSetLayout, fontsManager.getFontsTexture(),
                fontsTextureSampler, 0);

    }
    ...
    private void createUIResources(SwapChain swapChain, CommandPool commandPool, Queue queue) {
        ImGui.createContext();

        ImGuiIO imGuiIO = ImGui.getIO();
        imGuiIO.setIniFilename(null);
        VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
        imGuiIO.setDisplaySize(swapChainExtent.width(), swapChainExtent.height());
        imGuiIO.setDisplayFramebufferScale(1.0f, 1.0f);

        vertexBuffers = new VulkanBuffer[swapChain.getNumImages()];
        indicesBuffers = new VulkanBuffer[swapChain.getNumImages()];

        ImGuiIO io = ImGui.getIO();
        io.setKeyMap(ImGuiKey.Tab, GLFW_KEY_TAB);
        io.setKeyMap(ImGuiKey.LeftArrow, GLFW_KEY_LEFT);
        io.setKeyMap(ImGuiKey.RightArrow, GLFW_KEY_RIGHT);
        io.setKeyMap(ImGuiKey.UpArrow, GLFW_KEY_UP);
        io.setKeyMap(ImGuiKey.DownArrow, GLFW_KEY_DOWN);
        io.setKeyMap(ImGuiKey.PageUp, GLFW_KEY_PAGE_UP);
        io.setKeyMap(ImGuiKey.PageDown, GLFW_KEY_PAGE_DOWN);
        io.setKeyMap(ImGuiKey.Home, GLFW_KEY_HOME);
        io.setKeyMap(ImGuiKey.End, GLFW_KEY_END);
        io.setKeyMap(ImGuiKey.Insert, GLFW_KEY_INSERT);
        io.setKeyMap(ImGuiKey.Delete, GLFW_KEY_DELETE);
        io.setKeyMap(ImGuiKey.Backspace, GLFW_KEY_BACKSPACE);
        io.setKeyMap(ImGuiKey.Space, GLFW_KEY_SPACE);
        io.setKeyMap(ImGuiKey.Enter, GLFW_KEY_ENTER);
        io.setKeyMap(ImGuiKey.Escape, GLFW_KEY_ESCAPE);
        io.setKeyMap(ImGuiKey.KeyPadEnter, GLFW_KEY_KP_ENTER);

        fontsManager = new FontsManager(commandPool, queue);
    }

    public void recordCommandBuffer(Scene scene, CommandBuffer commandBuffer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int idx = swapChain.getCurrentFrame();

            IGuiInstance guiInstance = scene.getGuiInstance();
            if (guiInstance == null) {
                return;
            }
            guiInstance.drawGui(fontsManager);
            ...
        }
    }
    ...
}
```

Some things to notice, we will not need to store the fonts texture in the `GuiRenderActivity` class, this is managed now in the `FontsManager` class. Also when calling the gui instance, we need to pass the `FontsManager` instance so it can be used in the GUI building code. Therfore, the `IGuiInstance` interface needs to be changed: 

```java
package org.vulkanb.eng.scene;

import org.vulkanb.eng.graph.gui.FontsManager;

public interface IGuiInstance {
    void drawGui(FontsManager fontsManager);
}
```

Now, how do we use the fonts in the code? Prior to using a font we just need to call the `pushFont` function in Imgui, like this:
```java
ImGui.pushFont(fontsManager.getFont("MAIN"));
```

Once we have done with the font we just pop the font:
```java
ImGui.popFont();
```

A sample screenshot of the main screen is shown below.

<img src="../appendix-02/screen-shot.png" title="" alt="Screen Shot" data-align="center">