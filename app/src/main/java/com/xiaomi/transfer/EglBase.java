package com.xiaomi.transfer;

import android.graphics.SurfaceTexture;
import android.view.Surface;

import javax.microedition.khronos.egl.EGL10;

public abstract class EglBase {

    public static final Object lock = new Object();

    private static final int EGL_OPENGL_ES2_BIT = 4;
    static final int[] CONFIG_PLAIN = {
            EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,      // 渲染类型
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8, // 指定 Alpha 大小
            EGL10.EGL_DEPTH_SIZE, 8, // 指定深度(Z Buffer) 大小
            EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL10.EGL_NONE
    };

    static class Context {}

    public static EglBase create(Context shareContext, int[] configAttributes) {
        return (EglBase14.isEGL14Supported())
                && !(shareContext instanceof EglBase10.Context)
                ? new EglBase14((EglBase14.Context) shareContext, configAttributes)
                : new EglBase10((EglBase10.Context) shareContext, configAttributes);
    }

    public static EglBase create() {
        return create(null, CONFIG_PLAIN);
    }

    public static EglBase create(Context shareContext) {
        return create(shareContext, CONFIG_PLAIN);
    }

    public abstract void createSurface(Surface surface);

    public abstract void createSurface(SurfaceTexture surfaceTexture);

    public abstract void createPbufferSurface(int width, int height);

    public abstract Context getEglBaseContext();

    public abstract boolean hasSurface();

    public abstract int getSurfaceWidth();

    public abstract int getSurfaceHeight();

    public abstract void releaseSuface();

    public abstract void release();

    public abstract void makeCurrent();

    public abstract void detachCurrent();

    public abstract void swapBuffers();

    public abstract void setPresentTime(long nsecs);

//    public abstract EGLContext getEglContext();
    public static Context getCurrentContext() {
        if (EglBase14.isEGL14Supported()) {
            return EglBase14.getCurrentContext14();
        }
        return EglBase10.getCurrentContext10();
    }


}
