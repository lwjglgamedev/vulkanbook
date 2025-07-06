# Chapter 20 - Multi-thread

## Overview

In this chapter we will perform input processing and update and render code in separate threads. It is just a simple example which may help you in increasing parallelism even
more a graphics engine (having separate threads for different render tasks, etc.). In this case, we will use a new frame to perform render tasks, and reuse existing main
thread to process input and game update logic. Since we are using GLFW, any input related task needs to be done in the main thread. If you would want to process input in
a separate thread you would need first to access tio input state and then process that in that new thread. In order to keep code simple we will just use the same main thread.

But how we avoid to be updating entities information, such as model matrices, while we use that information in the render tasks? With latest changes we just dump entities
information into a buffer that will be used for render. We have been doing this prior to executing render tasks so there was no need to sync that. If we do it in parallel
we will use a simple trick to avoid that problem. If you recall, we have an array of buffers, one per frame in flight. What we will do is to use buffers associated to 
frame "n" for updating tasks, and frames associated with "n-1" when performing render tasks. It is like if we were updating the information associated to the next frame to be
rendered.

You can find the complete source code for this chapter [here](../../booksamples/chapter-20).

## Source code changes

Changes in the code are quite simple. We will start with the `Engine` class:

```java
public class Engine {

    private static final int EXECUTOR_THREADS = 1;
    ...
    private final ExecutorService executor;
    ...
    private final Callable<Void> renderTask;
    private int currentRenderFrame;
    private int currentUpdateFrame;

    public Engine(String windowTitle, IGameLogic appLogic) {
        ...
        executor = Executors.newFixedThreadPool(EXECUTOR_THREADS);
        renderTask = () -> {
            render.render(engCtx, currentUpdateFrame);
            return null;
        };
        currentUpdateFrame = 1;
        currentRenderFrame = 0;

        for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
            render.updateGlobalBuffers(engCtx, i);
        }
    }

    private void cleanup() {
        try {
            executor.shutdownNow();
            executor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Logger.error("Executor interrupted", ie);
        }
        ...
    }

    public void run() {
        var engCfg = EngCfg.getInstance();
        long initialTime = System.currentTimeMillis();
        float timeU = 1000.0f / engCfg.getUps();
        double deltaUpdate = 0;

        long updateTime = initialTime;
        Window window = engCtx.window();
        while (!window.shouldClose()) {
            long now = System.currentTimeMillis();
            deltaUpdate += (now - initialTime) / timeU;

            var future = executor.submit(renderTask);

            window.pollEvents();
            gameLogic.input(engCtx, now - initialTime);
            window.resetInput();

            if (deltaUpdate >= 1) {
                long diffTimeMillis = now - updateTime;
                gameLogic.update(engCtx, diffTimeMillis);
                updateTime = now;
                deltaUpdate--;
            }
            render.updateGlobalBuffers(engCtx, currentUpdateFrame);

            waitTasks(future);

            currentUpdateFrame = (currentUpdateFrame + 1) % VkUtils.MAX_IN_FLIGHT;
            currentRenderFrame = (currentRenderFrame + 1) % VkUtils.MAX_IN_FLIGHT;

            initialTime = now;
        }

        cleanup();
    }

    private void waitTasks(Future<Void> future) {
        try {
            future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

We will create an executor service, in this case we will need just only one worker thread to execute render tasks. In the constructor we just instantiate it
and create a callable tasks which will just call the `render` method over the `Render` class. We will have two variables to track frame update and frame render indices:
- `currentUpdateFrame`: Will refer to the index of the per frame in flight data that we will be using to update instances data in the main frame.
- `currentRenderFrame`: Will refer to the index of the per frame in flight data that we will be using to store render information in the render frame. It is one frame behind
the `currentUpdateFrame`.

At the end of the constructor we make sure that all instance data global buffers have been initialized for all the frames in flight. We will be updating and accessing
using different indices depending if we are in the main thread or in the render task.

Main loop is quiet similar, we just submit a render task and proceed with game state update and input processing. Once we have done that we just wait for all the submitted
tasks to the executor service, that is, the render task in our thread to finish. When cleaning up resources we need to shutdown the executor services and make sure that all
tasks have been finished.

In the `Render` class changes are quite minimal:

```java
public class Render {
    ...
    public Render(EngCtx engCtx) {
        vkCtx = new VkCtx(engCtx.window());

        graphQueue = new Queue.GraphicsQueue(vkCtx, 0);
        ...
    }
    ...
    public void render(EngCtx engCtx, int currentFrame) {
        SwapChain swapChain = vkCtx.getSwapChain();

        waitForFence(currentFrame);

        var cmdPool = cmdPools[currentFrame];
        var cmdBuffer = cmdBuffers[currentFrame];

        animRender.render(engCtx, vkCtx, modelsCache, animationsCache);

        recordingStart(cmdPool, cmdBuffer);

        scnRender.render(engCtx, vkCtx, cmdBuffer, globalBuffers, currentFrame);
        shadowRender.render(engCtx, vkCtx, cmdBuffer, globalBuffers, currentFrame);
        lightRender.render(engCtx, vkCtx, cmdBuffer, scnRender.getMrtAttachments(), shadowRender.getShadowAttachment(),
                shadowRender.getCascadeShadows(currentFrame), currentFrame);
        postRender.render(vkCtx, cmdBuffer, lightRender.getAttachment());
        guiRender.render(vkCtx, cmdBuffer, currentFrame, postRender.getAttachment());

        int imageIndex;
        if (resize || (imageIndex = swapChain.acquireNextImage(vkCtx.getDevice(), imageAqSemphs[currentFrame])) < 0) {
            resize(engCtx);
            return;
        }

        swapChainRender.render(vkCtx, cmdBuffer, postRender.getAttachment(), imageIndex);

        recordingStop(cmdBuffer);

        submit(cmdBuffer, currentFrame, imageIndex);

        resize = swapChain.presentImage(presentQueue, renderCompleteSemphs[imageIndex], imageIndex);
    }
    ...
    public void updateGlobalBuffers(EngCtx engCtx, int currentFrame) {
        globalBuffers.update(vkCtx, engCtx.scene(), modelsCache, animationsCache, materialsCache, currentFrame);
    }
    ...
}
```

We will remove the attribute `currentFrame` since we will receive it in the `render` method. In this method, we just do not call the `globalBuffers.update` since it will be
called in the `Engine` main loop by calling the new `updateGlobalBuffers` method.

And that's all. You can increase parallelism with this schema to update, for example, cascade shadows data, or any other render related task. This is just a simple example.

[Next chapter](../chapter-21/chapter-21.md)