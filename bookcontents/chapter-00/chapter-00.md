# Introduction

So what is Vulkan? Here's the definition for the Vulkan standard landing page:

> Vulkan is a new generation graphics and compute API that provides high-efficiency, cross-platform access to modern GPUs used in a wide variety of devices from PCs and consoles to mobile phones and embedded platforms.

Vulkan is a standard developed by the [Khronos group](https://www.khronos.org/), which is an open industry consortium behind many other well-known standards such as [OpenGL](https://www.khronos.org/opengl/) and [OpenCL](https://www.khronos.org/opencl/).

## Why Vulkan?

The first questions to come to mind are why Vulkan? Why adopt another cross-platform graphics API? Why not just stick with OpenGL, which is also cross-platform?

[![](https://imgs.xkcd.com/comics/standards.png)](https://xkcd.com/927/)

- It is as modern of an API as you can get, designed without the constraints of having to maintain backwards compatibility or legacy hardware. Take, for instance OpenGL, it is an aging API that has been evolving over the years and needs to support all parts of a graphics pipeline, from immediate modes to programmable pipelines.

- As a modern API, it has been designed with modern hardware capabilities in mind (GPUs and CPUs). For example, [concurrency support](https://en.wikipedia.org/wiki/Concurrency_(computer_science)) is part one of the strongest points of Vulkan. This dramatically improves the performance of applications that may now be CPU constrained by the single-threaded nature of some other APIs (such us OpenGL).

- It is a lower overhead API, in the sense that the most part of the work shall be explicitly done in the application. Hence, developers need to be very direct and precisely control every aspect. This simplifies the Vulkan drivers which provide a very thin layer on top of the hardware.

- Due to its lower overhead and its explicit nature, you will have direct control. You will get what you ask for, the drivers will not have to guess or assume about the next steps in your application, nor will it hold your hand. This will mean the differences between implementations and drivers may be much lower than in other APIs, resulting in more predictable and portable applications.

- It is indeed a platform-agnostic API not only for desktop computing but also for mobile platforms.

All that has been said above comes at a cost. It is an API that imposes a lot of responsibility onto the developers. And with great power, comes big responsibility. You will have to properly control everything, from memory allocation, to resource management, and to guarantee proper synchronization between your CPU and graphics card. As a result, you will have a more direct view about the GPU working inner details, which combined with the knowledge on how your application works can lead to great performance improvements.

The next question that may come to your mind may be, Is it Vulkan the right tool for you? The answer to this question depends on your skills and interests. If you are new to programming or want to obtain a rapid product, Vulkan is not the most adequate solution for you. As it has been already said, Vulkan is complex--you will have to invest lots of time understanding all the concepts, which can be difficult to understand for even moderate programmers. It is hard, but there will be a moment where all this complexity will start to fit in your mind and make sense. As a result, you will have a deeper knowledge of (and appreciation for) modern graphics applications and how GPUs work.

Besides complexity, other drawbacks of Vulkan may be:

- Verbosity. It is a very explicit API; you will have to specify every single detail, from available features, memory layouts, detailed pipeline execution, etc. This means that you will have to write a lot of code to manage it all.
- You may not directly translate concepts from other APIs to fully exploit Vulkan capabilities. This implies additional effort, specially if you have an existing codebase or assets.
- Its relatively new, so it is not so easy to find information about some specific topics.

Therefore, if you are new to programming, it is much better to start with some existing game engines such as [Unity](https://unity.com) or [Godot](https://godotengine.org/) or even to start with OpenGL. You can check my other book about OpenGL using LWJGL [here](https://ahbejarano.gitbook.io/lwjglgamedev/).

And the final question, why code in Java? Vulkan is a C based API, so usage of C/C++ is the ideal approach. I do not want to enter into a language war, but in my opinion, Java provides a good balance between easy of use, readability and maintainability. Having said that, I must admit that integration with native code in Java is quite verbose, and using libraries that require lots of pointers to `struct`'s, such as Vulkan, will require a bit of extra work.

## Prerequisites

This book assumes that you have a good understanding of Java language, and some previous knowledge of 3D graphics, such as OpenGL. 

We will use the following libraries:

- [LWJGL](https://www.lwjgl.org/) will provide the bindings to Vulkan and other required libraries (such as [GLFW](https://www.glfw.org/) for window and input management, and [STB](https://github.com/nothings/stb) for image loading).

- [JOML](https://github.com/JOML-CI/JOML) as the math library.

- [Maven](http://maven.apache.org/) to build the samples.

- [Tiny Log](https://tinylog.org/) for logging.

- [RenderDoc](https://renderdoc.org/) for graphics debugging.

- [GraalVM](https://www.graalvm.org) to generate native images (optional)

You will also need the following:

- The Vulkan [SDK](https://vulkan.lunarg.com/).

Bring your own IDE. However, since it is a multi-module project, it is advised to open each chapter independently. If you still want to work with the root project, please be aware that you will need to set the working directory to the root of each of the sub-projects you want to run. The resources (such as 3D models, shaders and textures are loaded using relative paths to the working directory).

The maven files support native image generation, through GraalVM, by activating the native profile. In order to activate this, you need to properly set up GraalVM in your environment and to install native building tools. Please, check GraalVM documentation for that. In order to generate native images, you just need to execute `mvn -Pnative clean package`.

## Resources used for writing this book

This book is the result of my self-learning process. I do not work on this domain professionally, rather I'm just a hobbyist with an interest in learning new technologies. As a result, you may find mistakes/bugs or even explanations that may be plain wrong. Please, feel free to contact me about that. My aim is that this book may help others in learning Vulkan.

There are multiple materials that I've consulted to write this book. The following list collects the ones that I've found more useful, and that I've consulted many times while leaning the Vulkan path:

- [The Vulkan tutorial](https://vulkan-tutorial.com/). This is a C based tutorial for Vulkan which describes in great detail the core concepts of the API.
- [Sascha Willems](https://github.com/SaschaWillems/Vulkan) Vulkan samples. This is an awesome collection of C++ samples which cover a huge set of Vulkan features.
- [Khronos Vulkan samples](https://github.com/KhronosGroup/Vulkan-Samples).
- LWJGL Vulkan [demos](https://github.com/LWJGL/lwjgl3-demos/tree/master/src/org/lwjgl/demo/vulkan).

[Next chapter](../chapter-01/chapter-01.md)
