package org.vulkanb.eng.scene;

import org.joml.Matrix4f;

import java.util.*;

public class Node {

    private final List<Node> children;

    private final String name;

    private final Node parent;

    private Matrix4f nodeTransformation;

    public Node(String name, Node parent, Matrix4f nodeTransformation) {
        this.name = name;
        this.parent = parent;
        this.nodeTransformation = nodeTransformation;
        this.children = new ArrayList<>();
    }

    public void addChild(Node node) {
        this.children.add(node);
    }

    public List<Node> getChildren() {
        return children;
    }

    public String getName() {
        return name;
    }

    public Matrix4f getNodeTransformation() {
        return nodeTransformation;
    }

    public Node getParent() {
        return parent;
    }
}
