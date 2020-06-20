# Setting the basis

In this chapter we will set up all the base code required to define a basic rendering loop. This game loop will constantly render new frames, get user inputs and update the game or application state. The code presented here is not directly related to Vulkan, you will see something similar in any other application independently of the specific API they use (This is the reason why we will mainly use large chunks of code here, without explaining step of step every detail.)

## Requirements

The base requirements to run the samples of this book are:

- Java version 14 or higher.
- Maven 3.6.X or higher to build the samples. Building the samples with maven will create a jar file, under the target folder, and the required folders with the dependencies and the resources. You can execute them from the command line just by using `java -jar <name_of_the_sample.jar>`.
- Using an IDE is optional. Personally I'm using [IntelliJ IDEA](https://www.jetbrains.com/es-es/idea/).

You can find the complete source code for this chapter [here](../../booksamples/chapter-01).

## Main class

So let's start from the beginning, let's start with our `Main` class:

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
    public void cleanUp() {
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

As you can see, in the `main` method, we just start our render / game engine, modeled by the `Engine` class. This class requires, in its constructor, the name of the application and a reference to the class that will implement the application logic. This is controlled by an interface `IAppLogic` interface which defines three methods:

- `init`:  Which is invoked upon application startup to create the required resources (meshes, textures, etc.).
- `handelInput`: Which is invoked periodically so the application can update its stated reacting to user input.
- `cleanUp`: Which is invoked when the application finished to properly release the acquired resources.

## Engine

This is the source code of the `Engine` class:

```java
package org.vulkanb.eng;

import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.scene.Scene;

public class Engine {

    private IAppLogic appLogic;
    private Render render;
    private boolean running;
    private Scene scene;
    private Window window;

    public Engine(String windowTitle, IAppLogic gameLogic) {
        this.window = new Window(windowTitle);
        this.render = new Render();
        this.appLogic = gameLogic;
        this.scene = new Scene(this.window);
        this.render.init(this.window);
        this.appLogic.init(this.window, this.scene, this.render);
    }

    private void cleanUp() {
        this.appLogic.cleanUp();
        this.render.cleanUp();
        this.window.cleanUp();
    }

    public void run() {
        EngineProperties engineProperties = EngineProperties.getInstance();
        long initialTime = System.nanoTime();
        double timeU = 1000000000d / engineProperties.getUps();
        double deltaU = 0;

        long updateTime = initialTime;
        while (this.running && !this.window.shouldClose()) {

            this.window.pollEvents();

            long currentTime = System.nanoTime();
            deltaU += (currentTime - initialTime) / timeU;
            initialTime = currentTime;

            if (deltaU >= 1) {
                long diffTimeNanos = currentTime - updateTime;
                this.appLogic.handleInput(this.window, this.scene, diffTimeNanos);
                updateTime = currentTime;
                deltaU--;
            }

            this.render.render(this.window, this.scene);
        }

        cleanUp();
    }

    public void start() {
        this.running = true;
        run();
    }

    public void stop() {
        this.running = false;
    }
}
```

Let's dissect what we are doing in the constructor. We firs create a `Window` class instance. The `Window` class is responsible of setting up a window using the [GLFW](https://www.glfw.org/) library and also allows us to retrieve user input. Then, we create an instance of the `Render` class which is responsible of performing the graphics rendering tasks. The `Scene` class instance will hold up all the scene items, camera settings and lights. After that, we invoke the `init` methods of the `Render` instance and the application logic.

Basically the engine class is a an infinite loop, modeled in the `run` method, which is triggered in the `start` method. This class also provides a `stop` method to get out of that loop and a `cleanUp` method to free resources when the loop exists.

But let's go back to the core method of the `Engine` class, the `run` method. We basically control the elapsed time since the last loop block to check if enough seconds have passed to update the state. If so, we calculate the elapsed time since the last update and invoke the `handleInput` method from the `IAppLogic` instance. We invoke the `render` method in each turn of the loop. We will see later on that we can limit the frame rate using the vsync or leave it uncapped.

You may have notice that we use a class named `EngineProperties` which in this case establishes the updates per second. This is a class that reads a properties file that will allow us to configure several parameters of the engine at run time. The code is pretty straight forward:

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
            this.ups = Integer.parseInt(props.getOrDefault("ups", DEFAULT_UPS).toString());
        } catch (IOException excp) {
            LOGGER.error("Could not read [{}] properties file", FILENAME, excp);
        }
    }

    public static synchronized EngineProperties getInstance() {
        if (instance== null) {
            instance= new EngineProperties();
        }
        return INSTANCE;
    }

    public int getUps() {
        return this.ups;
    }
}
```

By now the `Render` class is just an empty shell as well as the `Scene` class:

```java
package org.vulkanb.eng.graph;

import org.vulkanb.eng.Window;
import org.vulkanb.eng.scene.Scene;

public class Render {

    public void cleanUp() {
        // To be implemented
    }

    public void init(Window window) {
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

Now it's turn for our `Window` class. As it's been said before, this class mainly deals with window creation and input management. Besides that, this class is the first one that shows the first tiny bits of Vulkan. Let's start by examining its main attributes and constructor.

```java
package org.vulkanb.eng;

import org.lwjgl.glfw.*;
import org.lwjgl.system.MemoryUtil;

public class Window implements GLFWFramebufferSizeCallbackI {

    private int height;
    private GLFWKeyCallback keyCallback;
    private MouseInput mouseInput;
    private boolean resized;
    private int width;
    private long windowHandle;

    public Window(String title) {
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        if (!GLFWVulkan.glfwVulkanSupported()) {
            throw new IllegalStateException("Cannot find a compatible Vulkan installable client driver (ICD)");
        }

        GLFWVidMode vidMode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
        this.width = vidMode.width();
        this.height = vidMode.height();

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
        GLFW.glfwWindowHint(GLFW.GLFW_MAXIMIZED, GLFW.GLFW_FALSE);

        // Create the window
        this.windowHandle = GLFW.glfwCreateWindow(this.width, this.height, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (this.windowHandle == MemoryUtil.NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        GLFW.glfwSetFramebufferSizeCallback(this.windowHandle, this);

        this.keyCallback = new GLFWKeyCallback() {
            @Override
            public void invoke(long window, int key, int scancode, int action, int mods) {
                if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_RELEASE) {
                    GLFW.glfwSetWindowShouldClose(window, true);
                }
            }
        };
        GLFW.glfwSetKeyCallback(this.windowHandle, this.keyCallback);

        this.mouseInput = new MouseInput(this.windowHandle);
    }
```

The code it's self explanatory, we basically initialize GLFW, set up the window size to the primary monitor dimensions, create the window, set up key call backs (with a special case for signaling when window should close) and create a handler for mouse input. But, at the very beginning, there's a little fragment which checks if Vulkan is supported: 

```java
if (!GLFWVulkan.glfwVulkanSupported()) {
    throw new IllegalStateException("Cannot find a compatible Vulkan installable client driver (ICD)");
}
```

The code above, if the minimal requirements to use Vulkan are available (the Vulkan loader and a minimal functional ICD). This does not imply that Vulkan will work properly, but it is a minimum. Without this there is no sense in going on.The rest of the methods are basic ones to free resources, handling window resizing and getting access to the mouse handler.

```java
    public void cleanUp() {
        GLFW.glfwDestroyWindow(this.windowHandle);
        this.keyCallback.free();
        GLFW.glfwTerminate();
    }

    public int getHeight() {
        return this.height;
    }

    public MouseInput getMouseInput() {
        return this.mouseInput;
    }

    public int getWidth() {
        return this.width;
    }

    public long getWindowHandle() {
        return this.windowHandle;
    }

    @Override
    public void invoke(long handle, int width, int height) {
        resize(width, height);
    }

    public boolean isResized() {
        return this.resized;
    }

    public void pollEvents() {
        GLFW.glfwPollEvents();
        this.mouseInput.input();
    }

    public void resetResized() {
        this.resized = false;
    }

    public void resize(int width, int height) {
        this.resized = true;
        this.width = width;
        this.height = height;
        GLFW.glfwSetWindowSize(this.windowHandle, this.width, this.height);
    }

    public boolean shouldClose() {
        return GLFW.glfwWindowShouldClose(this.windowHandle);
    }
}
```

`MouseInput` is the class responsible of handling mouse input and clicks. It's code is also pretty straight forward.

```java
package org.vulkanb.eng;

import org.joml.Vector2f;

import static org.lwjgl.glfw.GLFW.*;

public class MouseInput {

    private Vector2f currentPos;
    private Vector2f displVec;
    private boolean inWindow;
    private boolean leftButtonPressed;
    private Vector2f previousPos;
    private boolean rightButtonPressed;

    public MouseInput(long windowHandle) {
        this.previousPos = new Vector2f(-1, -1);
        this.currentPos = new Vector2f();
        this.displVec = new Vector2f();
        this.leftButtonPressed = false;
        this.rightButtonPressed = false;
        this.inWindow = false;

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
        return this.currentPos;
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

If you run the sample you will get a nice black window that you can resize, move and close. With this this chapter comes to its end. In the next chapter we will start viewing the first basic Vulkan concepts.

[Next chapter](../chapter-02/chapter-02.md)