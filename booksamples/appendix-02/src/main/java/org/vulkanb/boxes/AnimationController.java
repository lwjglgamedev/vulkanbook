package org.vulkanb.boxes;

import org.vulkanb.eng.scene.*;

import java.util.*;

public class AnimationController {

    private List<AnimModel> animatedModelIds;

    public AnimationController() {
        animatedModelIds = new ArrayList<>();
    }

    public void addModel(ModelData modelData) {
        List<AnimData> animDataList = new ArrayList<>();
        for (ModelData.Animation animation : modelData.getAnimationsList()) {
            AnimData animData = new AnimData(animation.name(), animation.frameMillis(), animation.frames().size());
            animDataList.add(animData);
        }
        animatedModelIds.add(new AnimModel(modelData.getModelId(), animDataList));
    }

    public void update(Scene scene) {
        long now = System.currentTimeMillis();
        for (AnimModel animModel : animatedModelIds) {
            List<Entity> entities = scene.getEntitiesByModelId(animModel.id());
            for (Entity entity : entities) {
                Entity.EntityAnimation entityAnimation = entity.getEntityAnimation();
                if (!entityAnimation.isStarted()) {
                    continue;
                }
                AnimData animData = animModel.animDataList().get(entityAnimation.getAnimationIdx());
                long expirationTs = entityAnimation.getFrameStartTs() + (long) animData.frameMillis();
                if (expirationTs > now) {
                    continue;
                }
                int currentFrame = Math.floorMod(entityAnimation.getCurrentFrame() + 1, animData.numFrames());
                entityAnimation.setCurrentFrame(currentFrame);
            }
        }
    }

    record AnimData(String name, float frameMillis, int numFrames) {
    }

    record AnimModel(String id, List<AnimData> animDataList) {
    }
}
