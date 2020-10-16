[Previous chapter](../chapter-01/chapter-01.md)
# Setting Up The Basics

In this chapter we will set up all the base code required to define a basic rendering loop. This game loop will have these responsibilities: constantly render new frames; get user inputs; and update the game or application state. The code presented here is not directly related to Vulkan, but rather the starting point before we dive right in. You will see something similar in any other application independently of the specific API they use (this is the reason why we will mainly use large chunks of code here, without explaining step of step every detail).

## Requirements

The base requirements to run the samples of this book are:

- [Java version 15](https://jdk.java.net/15/) or higher.
- Maven 3.6.X or higher to build the samples. Building the samples with maven will create a jar file, under the target folder, and the required folders with the dependencies and the resources. You can execute them from the command line just by using `java -jar <name_of_the_sample.jar>`.
- Using an IDE is optional. Personally I'm using [IntelliJ IDEA](https://www.jetbrains.com/es-es/idea/). If you're using an IDE, you may let it compile and run it for you.

You can find the complete source code for this chapter [here](../../booksamples/chapter-01).

## Main class

So let's start from the beginning with, of all things, our `Main` class:

```java
package org.vulkanb;

import org.apache.logging.log4j.*;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.scene.Scene;

public class Main implements IAppLogic {

    private static final Logger LOGGER = LogManager.getLogger();

    public static void main(String[] args) {
        LOGGER.info("Starting application");
        Engine engine = new Engine("Vulkanbook", new Main());
        engine.start();
    }

    @Override
    public void cleanup() {
        // To be implemented
    }

    @Override
    public void handleInput(Window window, Scene scene, long diffTimeMilisec) {
        // To be implemented
    }

    @Override
    public void init(Window window, Scene scene, Render render) {
        // To be implemented
    }
}
```

As you can see, in the `main` method, we just start our render/game engine, modeled by the `Engine` class. This class requires—in its constructor—the name of the application, and a reference to the class which will implement the application logic. This is controlled by an interface `IAppLogic` which defines three methods:

- `init`: Invoked upon application startup to create the required resources (meshes, textures, etc).
- `handleInput`: Which is invoked periodically so that the application can update its state to react to user input.
- `cleanup`: Which is invoked when the application finished to properly release the acquired resources.

## Engine

This is the source code of the `Engine` class:

```java
package org.vulkanb.eng;

import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.scene.Scene;

public class Engine {

    private final IAppLogic appLogic;
    private final Render render;
    private final Scene scene;
    private final Window window;

    private boolean running;

    public Engine(String windowTitle, IAppLogic gameLogic) {
        window = new Window(windowTitle);
        render = new Render();
        appLogic = gameLogic;
        scene = new Scene(window);
        render.init(window, scene);
        appLogic.init(window, scene, render);
    }

    private void cleanup() {
        appLogic.cleanup();
        render.cleanup();
        window.cleanup();
    }

    public void run() {
        EngineProperties engineProperties = EngineProperties.getInstance();
        long initialTime = System.nanoTime();
        double timeU = 1000000000d / engineProperties.getUps();
        double deltaU = 0;

        long updateTime = initialTime;
        while (running && !window.shouldClose()) {

            window.pollEvents();

            long currentTime = System.nanoTime();
            deltaU += (currentTime - initialTime) / timeU;
            initialTime = currentTime;

            if (deltaU >= 1) {
                long diffTimeNanos = currentTime - updateTime;
                appLogic.handleInput(window, scene, diffTimeNanos);
                updateTime = currentTime;
                deltaU--;
            }

            render.render(window, scene);
        }

        cleanup();
    }

    public void start() {
        running = true;
        run();
    }

    public void stop() {
        running = false;
    }
}
```

Let's dissect what we are doing in the constructor. We first create a `Window` class instance. The `Window` will be responsible for setting up a window using the [GLFW](https://www.glfw.org/) library and allows us to retrieve user input. Then, we create an instance of the `Render` class which is responsible for performing the graphics rendering tasks. The `Scene` class instance will hold up all the scene items, camera settings and lights. After that, we invoke the `init` methods of the `Render` instance and the application logic.

Basically the engine class is an infinite loop, modeled in the `run` method, which is triggered in the `start` method. This class also provides a handy `stop` method to get out of said loop and a `cleanup` method to free resources when the loop exists.

Let's go back to the core method of the `Engine` class, the `run` method. We basically control the elapsed time since the last loop block to check if enough seconds have passed to update the state. If so, we calculate the elapsed time since the last update and invoke the `handleInput` method from the `IAppLogic` instance. We invoke the `render` method in each turn of the loop. Later on we will be able to limit the frame rate using vsync, or leave it uncapped.

You may have notice that we use a class named `EngineProperties`, which in this case establishes the updates per second. This is a class which reads a property file that will allow us to configure several parameters of the engine at runtime. The code is pretty straight forward:

```java
package org.vulkanb.eng;

import org.apache.logging.log4j.*;

import java.io.*;
import java.util.Properties;

public class EngineProperties {
    private static final int DEFAULT_UPS = 30;
    private static final String FILENAME = "eng.properties";
    private static final Logger LOGGER = LogManager.getLogger();
    private static EngineProperties instance;
    private int ups;

    private EngineProperties() {
        // Singleton
        Properties props = new Properties();

        try (InputStream stream = EngineProperties.class.getResourceAsStream("/" + FILENAME)) {
            props.load(stream);
            ups = Integer.parseInt(props.getOrDefault("ups", DEFAULT_UPS).toString());
        } catch (IOException excp) {
            LOGGER.error("Could not read [{}] properties file", FILENAME, excp);
        }
    }

    public static synchronized EngineProperties getInstance() {
        if (instance == null) {
            instance = new EngineProperties();
        }
        return instance;
    }

    public int getUps() {
        return ups;
    }
}
```

At this point, the `Render` and `Scene` classes are both just an empty shell:

```java
package org.vulkanb.eng.graph;

import org.vulkanb.eng.Window;
import org.vulkanb.eng.scene.Scene;

public class Render {

    public void cleanup() {
        // To be implemented
    }

    public void init(Window window, Scene scene) {
        // To be implemented
    }

    public void render(Window window, Scene scene) {
        // To be implemented
    }
}
```

```java
package org.vulkanb.eng.scene;

import org.vulkanb.eng.Window;

public class Scene {

    public Scene(Window window) {

    }
}
```

## Window

Now it's the turn for our `Window` class. As it's been said before, this class mainly deals with window creation and input management. Alongside that, this class is the first one which shows the first tiny bits of Vulkan. Let's start by examining its main attributes and constructor.

```java
package org.vulkanb.eng;

import org.lwjgl.glfw.*;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported;

public class Window {

    private final MouseInput mouseInput;
    private final long windowHandle;

    private boolean resized;
    private int width, height;

    public Window(String title) {
        this(title, null);
    }

    public Window(String title, GLFWKeyCallbackI keyCallback) {
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        if (!glfwVulkanSupported()) {
            throw new IllegalStateException("Cannot find a compatible Vulkan installable client driver (ICD)");
        }

        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        width = vidMode.width();
        height = vidMode.height();

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_FALSE);

        // Create the window
        windowHandle = glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (windowHandle == MemoryUtil.NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        glfwSetFramebufferSizeCallback(windowHandle, (window, width, height) -> resize(width, height));

        glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true);
            }
            if (keyCallback != null) {
                keyCallback.invoke(window, key, scancode, action, mods);
            }
        });

        mouseInput = new MouseInput(windowHandle);
    }
    ...
}
```

The code is self-explanatory—we basically initialize GLFW, set up the window size to the primary monitor dimensions, create the window, set up key callbacks (with a special case for signaling when window should close) and create a handler for mouse input. At the very beginning, there's a little fragment which checks if Vulkan is supported:

```java
if (!GLFWVulkan.glfwVulkanSupported()) {
    throw new IllegalStateException("Cannot find a compatible Vulkan installable client driver (ICD)");
}
```

The code above will test if the minimal requirements to use Vulkan are available (the Vulkan loader and a minimal functional ICD). This does not imply that Vulkan will work properly, but it is a minimum. Without this there is no sense in going on. The rest of the methods are getters & setters, and basic ones to free resources, handling window resizing, etc.

```java
public class Window {
    ...
    public void cleanup() {
        glfwDestroyWindow(windowHandle);
        glfwTerminate();
    }

    public int getHeight() {
        return height;
    }

    public MouseInput getMouseInput() {
        return mouseInput;
    }

    public int getWidth() {
        return width;
    }

    public long getWindowHandle() {
        return windowHandle;
    }
    
    public boolean isKeyPressed(int keyCode) {
        return glfwGetKey(windowHandle, keyCode) == GLFW_PRESS;
    }

    public boolean isResized() {
        return resized;
    }

    public void pollEvents() {
        glfwPollEvents();
        mouseInput.input();
    }

    public void resetResized() {
        resized = false;
    }

    public void resize(int width, int height) {
        resized = true;
        this.width = width;
        this.height = height;
    }

    public void setResized(boolean resized) {
        this.resized = resized;
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(windowHandle);
    }
}
```

`MouseInput` is the class responsible for handling mouse input and clicks. Its code is also pretty straight forward.

```java
package org.vulkanb.eng;

import org.joml.Vector2f;

import static org.lwjgl.glfw.GLFW.*;

public class MouseInput {

    private final Vector2f currentPos, previousPos;
    private final Vector2f displVec;

    private boolean inWindow;
    private boolean leftButtonPressed;
    private boolean rightButtonPressed;

    public MouseInput(long windowHandle) {
        previousPos = new Vector2f(-1, -1);
        currentPos = new Vector2f();
        displVec = new Vector2f();
        leftButtonPressed = false;
        rightButtonPressed = false;
        inWindow = false;

        glfwSetCursorPosCallback(windowHandle, (handle, xpos, ypos) -> {
            currentPos.x = (float) xpos;
            currentPos.y = (float) ypos;
        });
        glfwSetCursorEnterCallback(windowHandle, (handle, entered) -> inWindow = entered);
        glfwSetMouseButtonCallback(windowHandle, (handle, button, action, mode) -> {
            leftButtonPressed = button == GLFW_MOUSE_BUTTON_1 && action == GLFW_PRESS;
            rightButtonPressed = button == GLFW_MOUSE_BUTTON_2 && action == GLFW_PRESS;
        });
    }

    public Vector2f getCurrentPos() {
        return currentPos;
    }

    public Vector2f getDisplVec() {
        return displVec;
    }

    public void input() {
        displVec.x = 0;
        displVec.y = 0;
        if (previousPos.x > 0 && previousPos.y > 0 && inWindow) {
            double deltax = currentPos.x - previousPos.x;
            double deltay = currentPos.y - previousPos.y;
            boolean rotateX = deltax != 0;
            boolean rotateY = deltay != 0;
            if (rotateX) {
                displVec.y = (float) deltax;
            }
            if (rotateY) {
                displVec.x = (float) deltay;
            }
        }
        previousPos.x = currentPos.x;
        previousPos.y = currentPos.y;
    }

    public boolean isLeftButtonPressed() {
        return leftButtonPressed;
    }

    public boolean isRightButtonPressed() {
        return rightButtonPressed;
    }
}
```

How you want to handle user input is largely up to you. In order to get them, you can call `glfwSet*Callback()` with a method (or lambda) or invoke the `isKeyPressed` method.

If you run the sample you will get a nice black window that you can resize, move and close. With that, this chapter comes to its end. In the next chapter we will start viewing the first basic Vulkan concepts.

[Next chapter](../chapter-02/chapter-02.md)