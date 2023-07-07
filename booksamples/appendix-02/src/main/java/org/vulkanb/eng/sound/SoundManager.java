package org.vulkanb.eng.sound;

import org.joml.*;
import org.lwjgl.openal.*;
import org.tinylog.Logger;
import org.vulkanb.eng.scene.Camera;

import java.nio.*;
import java.util.*;

import static org.lwjgl.openal.AL10.alDistanceModel;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class SoundManager {

    private final long context;
    private final long device;
    private final Map<String, SoundBuffer> soundBufferMap;
    private final Map<String, SoundSource> soundSourceMap;
    private SoundListener listener;

    public SoundManager() {
        soundBufferMap = new HashMap<>();
        soundSourceMap = new HashMap<>();

        device = alcOpenDevice((ByteBuffer) null);
        if (device == NULL) {
            throw new IllegalStateException("Failed to open the default OpenAL device.");
        }
        ALCCapabilities deviceCaps = ALC.createCapabilities(device);
        this.context = alcCreateContext(device, (IntBuffer) null);
        if (context == NULL) {
            throw new IllegalStateException("Failed to create OpenAL context.");
        }
        alcMakeContextCurrent(context);
        AL.createCapabilities(deviceCaps);
    }

    public void addSoundBuffer(String name, SoundBuffer soundBuffer) {
        this.soundBufferMap.put(name, soundBuffer);
    }

    public void addSoundSource(String name, SoundSource soundSource) {
        this.soundSourceMap.put(name, soundSource);
    }

    public void cleanup() {
        soundSourceMap.values().forEach(SoundSource::cleanup);
        soundSourceMap.clear();
        soundBufferMap.values().forEach(SoundBuffer::cleanup);
        soundBufferMap.clear();
        if (context != NULL) {
            alcDestroyContext(context);
        }
        if (device != NULL) {
            alcCloseDevice(device);
        }
    }

    public SoundListener getListener() {
        return this.listener;
    }

    public SoundSource getSoundSource(String name) {
        return this.soundSourceMap.get(name);
    }

    public void pause(String sourceId) {
        SoundSource soundSource = soundSourceMap.get(sourceId);
        if (soundSource == null) {
            Logger.warn("Unknown source [{}]", sourceId);
            return;
        }
        soundSource.pause();
    }

    public void play(String sourceId, String bufferId) {
        SoundSource soundSource = soundSourceMap.get(sourceId);
        if (soundSource == null) {
            Logger.warn("Unknown source [{}]", sourceId);
            return;
        }
        SoundBuffer soundBuffer = soundBufferMap.get(bufferId);
        if (soundBuffer == null) {
            Logger.warn("Unknown buffer [{}]", bufferId);
            return;
        }
        soundSource.setBuffer(soundBuffer.getBufferId());
        soundSource.play();
    }

    public void playSoundSource(String name) {
        SoundSource soundSource = this.soundSourceMap.get(name);
        if (soundSource != null && !soundSource.isPlaying()) {
            soundSource.play();
        }
    }

    public void removeSoundSource(String name) {
        this.soundSourceMap.remove(name);
    }

    public void setAttenuationModel(int model) {
        alDistanceModel(model);
    }

    public void setListener(SoundListener listener) {
        this.listener = listener;
    }

    public void stop(String sourceId) {
        SoundSource soundSource = soundSourceMap.get(sourceId);
        if (soundSource == null) {
            Logger.warn("Unknown source [{}]", sourceId);
            return;
        }
        soundSource.stop();
    }

    public void updateListenerPosition(Camera camera) {
        Matrix4f viewMatrix = camera.getViewMatrix();
        listener.setPosition(camera.getPosition());
        Vector3f at = new Vector3f();
        viewMatrix.positiveZ(at).negate();
        Vector3f up = new Vector3f();
        viewMatrix.positiveY(up);
        listener.setOrientation(at, up);
    }
}
