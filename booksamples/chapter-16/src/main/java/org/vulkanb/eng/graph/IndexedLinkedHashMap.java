package org.vulkanb.eng.graph;

import java.util.*;

public class IndexedLinkedHashMap<K, V> extends LinkedHashMap<K, V> {

    private final List<K> indexList = new ArrayList<>();

    public int getIndexOf(K key) {
        return indexList.indexOf(key);
    }

    public V getValueAtIndex(int i) {
        return super.get(indexList.get(i));
    }

    @Override
    public V put(K key, V val) {
        if (!super.containsKey(key)) indexList.add(key);
        return super.put(key, val);
    }
}
