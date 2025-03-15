package org.vulkanb.eng.graph.vk;

import org.lwjgl.vulkan.VkPhysicalDeviceLimits;
import org.tinylog.Logger;
import org.vulkanb.eng.EngCfg;

import java.util.*;

import static org.lwjgl.vulkan.VK13.*;

public class DescAllocator {

    private final Map<Integer, Integer> descLimits;
    private final List<DescPoolInfo> descPoolList;
    private final Map<String, DescSetInfo> descSetInfoMap;

    public DescAllocator(PhysDevice physDevice, Device device) {
        Logger.debug("Creating descriptor allocator");
        descPoolList = new ArrayList<>();
        descLimits = createDescLimits(physDevice);
        descPoolList.add(createDescPoolInfo(device, descLimits));
        descSetInfoMap = new HashMap<>();
    }

    private static Map<Integer, Integer> createDescLimits(PhysDevice physDevice) {
        var engCfg = EngCfg.getInstance();
        int maxDescs = engCfg.getMaxDescs();
        VkPhysicalDeviceLimits limits = physDevice.getVkPhysicalDeviceProperties().properties().limits();
        Map<Integer, Integer> descLimits = new HashMap<>();
        descLimits.put(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, Math.min(maxDescs, limits.maxDescriptorSetUniformBuffers()));
        descLimits.put(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, Math.min(maxDescs, limits.maxDescriptorSetSamplers()));
        descLimits.put(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, Math.min(maxDescs, limits.maxDescriptorSetStorageBuffers()));
        return descLimits;
    }

    private static DescPoolInfo createDescPoolInfo(Device device, Map<Integer, Integer> descLimits) {
        Map<Integer, Integer> descCount = new HashMap<>();
        List<DescPool.DescTypeCount> descTypeCounts = new ArrayList<>();
        descLimits.forEach((k, v) -> {
            descCount.put(k, v);
            descTypeCounts.add(new DescPool.DescTypeCount(k, v));
        });
        var descPool = new DescPool(device, descTypeCounts);
        return new DescPoolInfo(descCount, descPool);
    }

    public synchronized DescSet addDescSet(Device device, String id, DescSetLayout descSetLayout) {
        return addDescSets(device, id, 1, descSetLayout)[0];
    }

    public synchronized DescSet[] addDescSets(Device device, String id, int count, DescSetLayout descSetLayout) {
        // Check if we have room for the sets in any descriptor pool
        DescPoolInfo targetPool = null;
        int poolPos = 0;
        for (DescPoolInfo descPoolInfo : descPoolList) {
            targetPool = descPoolInfo;
            for (DescSetLayout.LayoutInfo layoutInfo : descSetLayout.getLayoutInfos()) {
                int descType = layoutInfo.descType();
                Integer available = descPoolInfo.descCount.get(descType);
                if (available == null) {
                    throw new RuntimeException("Unknown type [" + descType + "]");
                }
                Integer maxTotal = descLimits.get(descType);
                if (count > maxTotal) {
                    throw new RuntimeException("Cannot create more than [" + maxTotal + "] for descriptor type [" + descType + "]");
                }
                if (available < count) {
                    targetPool = null;
                    break;
                }
            }
            if (targetPool != null) {
                break;
            }
            poolPos++;
        }

        if (targetPool == null) {
            targetPool = createDescPoolInfo(device, descLimits);
            descPoolList.add(targetPool);
            poolPos++;
        }

        var result = new DescSet[count];
        for (int i = 0; i < count; i++) {
            DescSet descSet = new DescSet(device, targetPool.descPool(), descSetLayout);
            result[i] = descSet;
        }
        descSetInfoMap.put(id, new DescSetInfo(result, poolPos));

        // Update consumed descriptors
        for (DescSetLayout.LayoutInfo layoutInfo : descSetLayout.getLayoutInfos()) {
            int descType = layoutInfo.descType();
            targetPool.descCount.put(descType, targetPool.descCount.get(descType) - count);
        }

        return result;
    }

    public synchronized void cleanup(Device device) {
        Logger.debug("Destroying descriptor allocator");
        descSetInfoMap.clear();
        descPoolList.forEach(d -> d.descPool.cleanup(device));
    }

    public synchronized void freeDescSet(Device device, String id) {
        DescSetInfo descSetInfo = descSetInfoMap.get(id);
        if (descSetInfo == null) {
            Logger.info("Could not find descriptor set with id [{}]", id);
            return;
        }
        if (descSetInfo.poolPos >= descPoolList.size()) {
            Logger.info("Could not find descriptor pool associated to set with id [{}]", id);
            return;
        }
        DescPoolInfo descPoolInfo = descPoolList.get(descSetInfo.poolPos);
        Arrays.asList(descSetInfo.descSets).forEach(d -> descPoolInfo.descPool.freeDescriptorSet(device, d.getVkDescriptorSet()));
    }

    public synchronized DescSet getDescSet(String id, int pos) {
        DescSet result = null;
        DescSetInfo descSetInfo = descSetInfoMap.get(id);
        if (descSetInfo != null) {
            result = descSetInfo.descSets()[pos];
        }
        return result;
    }

    public synchronized DescSet getDescSet(String id) {
        return getDescSet(id, 0);
    }

    record DescPoolInfo(Map<Integer, Integer> descCount, DescPool descPool) {
    }

    record DescSetInfo(DescSet[] descSets, int poolPos) {
    }
}
