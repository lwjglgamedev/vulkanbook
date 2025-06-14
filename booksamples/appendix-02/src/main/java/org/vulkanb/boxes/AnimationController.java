package org.vulkanb.boxes;

import org.vulkanb.eng.EngCtx;
import org.vulkanb.eng.model.*;
import org.vulkanb.eng.scene.*;

import java.util.*;

public class AnimationController {

    private List<AnimModel> animatedModelIds;

    public AnimationController() {
        animatedModelIds = new ArrayList<>();
    }

    public void addModel(ModelData modelData) {
        List<AnimData> animDataList = new ArrayList<>();
        for (Animation animation : modelData.animations()) {
            AnimData animData = new AnimData(animation.name(), animation.frameMillis(), animation.frames().size());
            animDataList.add(animData);
        }
        animatedModelIds.add(new AnimModel(modelData.id(), animDataList));
    }

    public void update(EngCtx engCtx) {
        long now = System.currentTimeMillis();
        Scene scene = engCtx.scene();
        for (AnimModel animModel : animatedModelIds) {
            List<Entity> entities = scene.getEntities().get(animModel.id());
            for (Entity entity : entities) {
                EntityAnimation entityAnimation = entity.getEntityAnimation();
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
