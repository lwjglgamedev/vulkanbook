# Chapter 11 - Post processing

In this chapter we will implement a post-processing stage. We will render to a buffer instead of directly rendering to a swp chain image and once, we have finished we
will apply some effects suc us FXAA filtering and gamma correction.

You can find the complete source code for this chapter [here](../../booksamples/chapter-11).

## Rendering to an attachment

We will start first by modifying the `ScnRender` class to use its own attachment for color output instead of using swap chain images. If you recall we already did have
attachments for depth information. The changes in the `ScnRender` class start like this:

```java
```

[Next chapter](../chapter-12/chapter-12.md)