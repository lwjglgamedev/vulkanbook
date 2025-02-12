# Appendix 01 - Troubleshooting device loss error

In this appendix we will provide some insights on how to troubleshoot Vulkan device loss errors. The root cause of device loss errors may be hard to track, therefore, Vulkan provides some debugging extensions to help developers track down these errors.

You can find the complete source code for this chapter [here](../../booksamples/appendix-01).

## Enabling the extensions

The specific extensions to be used will depend in the GPU family that you have. If you have an Nvida GPU, the extensions and functions to be used will be different than if you have an AMD GPU. In any case, the process is more or less similar. You store certain checkpoints or mark in a command buffer that you can check later on if something goes wrong. The last executed mark will give you a hint on where things started to fail.

Therefore, the first step is to get, from the physical device, which type of extension is supported for the associated GPU.  In the `PhysicalDevice` class we will create a new method to check the supported extension. This method will be invoked when creating the `PhysicalDevice` instance so it can be checked later on:

```java
public class PhysicalDevice {
    ...
    private CheckPointExtension checkPointExtension;
    ...
    private PhysicalDevice(VkPhysicalDevice vkPhysicalDevice) {
        ...
            checkPointExtension = calCheckPointExtension(vkDeviceExtensions);
        ...
    }
    ...
    private CheckPointExtension calCheckPointExtension(VkExtensionProperties.Buffer vkDeviceExtensions) {
        CheckPointExtension result = CheckPointExtension.NONE;

        int numExtensions = vkDeviceExtensions != null ? vkDeviceExtensions.capacity() : 0;
        for (int i = 0; i < numExtensions; i++) {
            String extensionName = vkDeviceExtensions.get(i).extensionNameString();
            if (NVDeviceDiagnosticCheckpoints.VK_NV_DEVICE_DIAGNOSTIC_CHECKPOINTS_EXTENSION_NAME.equals(extensionName)) {
                result = CheckPointExtension.NVIDIA;
                break;
            } else if (AMDBufferMarker.VK_AMD_BUFFER_MARKER_EXTENSION_NAME.equals(extensionName)) {
                result = CheckPointExtension.AMD;
                break;
            }
        }
        return result;
    }
    ...
    public CheckPointExtension getCheckPointExtension() {
        return checkPointExtension;
    }
    ...
    public enum CheckPointExtension {
        NONE, NVIDIA, AMD;
    }
}
```

As you can see, we just iterate over the device extensions to check if it supports the required extension for NVIDIA (`NVDeviceDiagnosticCheckpoints.VK_NV_DEVICE_DIAGNOSTIC_CHECKPOINTS_EXTENSION_NAME`) or AMD (`AMDBufferMarker.VK_AMD_BUFFER_MARKER_EXTENSION_NAME`).

We will create a new property to control if we want to enable the checkpoint / buffer marker extension:
```java
public class EngineProperties {
    ...
    private boolean enableCheckPoints = false;
    ...
    private EngineProperties() {
        ...
            enableCheckPoints = Boolean.parseBoolean(props.getOrDefault("enableCheckPoints", false).toString());
        ...
    }
    ...
    public boolean isEnableCheckPoints() {
        return enableCheckPoints;
    }    
    ...
}
```

In the `Device` class we will enable the checkpoint / buffer marker extension if properly set up and supported by the GPU:
```java
public class Device {
    ...
    public Device(Instance instance, PhysicalDevice physicalDevice) {
        ...
            // Define required extensions
            EngineProperties engineProperties = EngineProperties.getInstance();
            boolean enableCheckPoints = engineProperties.isEnableCheckPoints();
            PhysicalDevice.CheckPointExtension checkPointExtension = physicalDevice.getCheckPointExtension();
            if (enableCheckPoints && checkPointExtension == PhysicalDevice.CheckPointExtension.NONE) {
                Logger.warn("Requested check point extensions but not supported by device");
                enableCheckPoints = false;
            }

            int numRequiredExtensions = 1;
            Set<String> deviceExtensions = getDeviceExtensions();
            boolean usePortability = deviceExtensions.contains(VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME) && VulkanUtils.getOS() == VulkanUtils.OSType.MACOS;
            if (usePortability) {
                numRequiredExtensions++;
            }
            if (enableCheckPoints) {
                numRequiredExtensions++;
            }
            PointerBuffer requiredExtensions = stack.mallocPointer(numRequiredExtensions);
            requiredExtensions.put(stack.ASCII(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME));
            if (usePortability) {
                requiredExtensions.put(stack.ASCII(VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME));
            }
            if (enableCheckPoints) {
                if (checkPointExtension == PhysicalDevice.CheckPointExtension.NVIDIA) {
                    requiredExtensions.put(stack.ASCII(NVDeviceDiagnosticCheckpoints.VK_NV_DEVICE_DIAGNOSTIC_CHECKPOINTS_EXTENSION_NAME));
                } else {
                    requiredExtensions.put(stack.ASCII(AMDBufferMarker.VK_AMD_BUFFER_MARKER_EXTENSION_NAME));
                }
            }
            requiredExtensions.flip();
        ...
    }
    ...
}
```

## Use the extensions

Finally, we will need to add new methods to support the insertion of checkpoint / markers and to dump the results. We will do this in the `VulkanUtils` class:
```java
public class VulkanUtils {
    ...
    public static List<CheckPoint> dumpCheckPoints(Queue queue) {
        List<CheckPoint> result = new ArrayList<>();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.callocInt(1);
            vkGetQueueCheckpointDataNV(queue.getVkQueue(), count, null);
            int numCheckPoints = count.get(0);
            if (numCheckPoints > 0) {
                VkCheckpointDataNV.Buffer checkpointData = VkCheckpointDataNV.calloc(numCheckPoints, stack);
                checkpointData.stream().forEach(c -> c.sType(VK_STRUCTURE_TYPE_CHECKPOINT_DATA_NV));

                vkGetQueueCheckpointDataNV(queue.getVkQueue(), count, checkpointData);
                checkpointData.forEach(c -> result.add(new CheckPoint(c.pCheckpointMarker(), c.stage())));
            }
        }
        return result;
    }

    public static void insertBufferMarker(Device device, CommandBuffer cmdBuff, int pipelineStage, VulkanBuffer dstBuffer,
                                          int offset, int marker) {
        PhysicalDevice.CheckPointExtension checkPointExtension = device.getPhysicalDevice().getCheckPointExtension();
        if (checkPointExtension == PhysicalDevice.CheckPointExtension.AMD) {
            vkCmdWriteBufferMarkerAMD(cmdBuff.getVkCommandBuffer(), pipelineStage, dstBuffer.getBuffer(), offset,
                    marker);
        } else {
            Logger.warn("Requested debug buffer marker in non supported device");
        }
    }

    public static void insertDebugCheckPoint(Device device, CommandBuffer cmdBuff, long checkPointMarker) {
        PhysicalDevice.CheckPointExtension checkPointExtension = device.getPhysicalDevice().getCheckPointExtension();
        if (checkPointExtension == PhysicalDevice.CheckPointExtension.NVIDIA) {
            vkCmdSetCheckpointNV(cmdBuff.getVkCommandBuffer(), checkPointMarker);
        } else {
            Logger.warn("Requested debug check point in non supported device");
        }
    }
    ...

    public record CheckPoint(long marker, int stage) {
    }
}
```

The `insertBufferMarker` can be used to insert buffer markers into a command buffer for AMD GPUs. It calls the `vkCmdWriteBufferMarkerAMD` function which receives the following parameters:

- The command buffer into which the marker is recorded.
- The pipeline stage whose completion triggers the marker write.
- A buffer where the marker will be written to.
- An offset to that buffer.
- The marker itself, which is a 32 bit value.

The `insertDebugCheckPoint` is the equivalent one for NVIDIA GPUs. In this case, it calls the `vkCmdSetCheckpointNV` which just needs a command buffer and a checkpoint marker (a long value). In this case, since the markers are not written to another buffer, we need to dump the status of the checkpoints by calling the `dumpCheckPoints` which retrieves the most recent diagnostic checkpoints that were executed by the device.