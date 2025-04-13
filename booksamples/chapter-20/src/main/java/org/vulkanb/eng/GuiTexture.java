package org.vulkanb.eng;


import java.security.SecureRandom;

public record GuiTexture(long id, String texturePath) {
    public GuiTexture(String texturePath) {
        this(getId(), texturePath);
    }

    private static long getId() {
        SecureRandom secureRandom = new SecureRandom();
        long id = Math.abs(secureRandom.nextLong());
        if (id == 0) {
            id += 1;
        }
        return id;
    }
}
