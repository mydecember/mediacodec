package com.xiaomi.drawers;

import android.opengl.GLES30;

import com.xiaomi.glbase.GlUtil;
import com.xiaomi.glbase.GlesUtil;

public class GLFrameBuffer {
    private int mFbo;
    private int mTexture;
    private int mWidth;
    private int mHeight;
    private int mType = GLES30.GL_RGBA;
    public void initiate(int width, int height, int type) {
        mWidth = width;
        mHeight = height;
        if (type != 0) {
            mType = type;
        }

        mFbo = GlesUtil.createFrameBuffer();
        mTexture = GlesUtil.createFrameTexture(width, height);
    }

    public void release() {
        GlesUtil.deleteFrameBuffer(mFbo, mTexture);
    }

    public int getTexture() {
        return mTexture;
    }
    public int getFBO() {
        return mFbo;
    }

    public boolean bind() {
        GlesUtil.bindFrameBuffer(mFbo, mTexture);
        GlUtil.checkGlError("bind error ");
        return true;
    }

    public boolean unBind() {
        GlesUtil.bindFrameBuffer(mFbo, 0);
        return true;
    }
}
