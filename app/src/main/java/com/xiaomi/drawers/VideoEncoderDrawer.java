package com.xiaomi.drawers;
import android.opengl.GLES30;
import android.util.Log;

import com.xiaomi.glbase.GlUtil;
import com.xiaomi.glbase.GlesUtil;

public class VideoEncoderDrawer extends BaseDrawer {
    private static String TAG = "VideoEncoderDrawer";
    // 绘制的纹理 ID
    private int mTextureId;
    private int av_Position;
    private int af_Position;
    private int s_Texture;

    @Override
    public void release() {
        super.release();
    }

    @Override
    public void setInputTextureId(int textureId) {
        this.mTextureId = textureId;
        //Log.d(TAG, "setInputTextureId: " + textureId);
    }

    @Override
    public int getOutputTextureId() {
        return mTextureId;
    }

    @Override
    public void create() {
        super.create();
    }

    @Override
    public void surfaceChangedSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void drawFrame(long timeStamp) {
        onDraw();
    }

    @Override
    protected void onCreated() {
        mProgram = GlesUtil.createProgram(getVertexSource(), getFragmentSource());
        initVertexBufferObjects();
        av_Position = GLES30.glGetAttribLocation(mProgram, "av_Position");
        af_Position = GLES30.glGetAttribLocation(mProgram, "af_Position");
        s_Texture = GLES30.glGetUniformLocation(mProgram, "s_Texture");
        Log.d(TAG, "onCreated: av_Position " + av_Position);
        Log.d(TAG, "onCreated: af_Position " + af_Position);
        Log.d(TAG, "onCreated: s_Texture " + s_Texture);
        Log.e(TAG, "onCreated: error " + GLES30.glGetError());
    }

    @Override
    protected void onChanged(int width, int height) {

    }

    @Override
    protected void onDraw() {
        GlUtil.checkGlError("to onDraw");
        clear();
        useProgram();
        viewPort(0, 0, width, height);

        GLES30.glEnableVertexAttribArray(av_Position);
        GLES30.glEnableVertexAttribArray(af_Position);
//        GLES30.glVertexAttribPointer(av_Position, CoordsPerVertexCount, GLES30.GL_FLOAT, false, VertexStride, mVertexBuffer);
//        GLES30.glVertexAttribPointer(af_Position, CoordsPerTextureCount, GLES30.GL_FLOAT, false, TextureStride, mDisplayTextureBuffer);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mVertexBufferId);
        GLES30.glVertexAttribPointer(av_Position, CoordsPerVertexCount, GLES30.GL_FLOAT, false, 0, 0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mDisplayTextureBufferId);
        GLES30.glVertexAttribPointer(af_Position, CoordsPerTextureCount, GLES30.GL_FLOAT, false, 0, 0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTextureId);
        GLES30.glUniform1i(s_Texture, 0);
        // 绘制 GLES30.GL_TRIANGLE_STRIP:复用坐标
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, VertexCount);
        GLES30.glDisableVertexAttribArray(av_Position);
        GLES30.glDisableVertexAttribArray(af_Position);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
    }

    @Override
    protected String getVertexSource() {
        final String source = "attribute vec4 av_Position; " +
                "attribute vec2 af_Position; " +
                "varying vec2 v_texPo; " +
                "void main() { " +
                "    v_texPo = af_Position; " +
                "    gl_Position = av_Position; " +
                "}";
        return source;
    }

    @Override
    protected String getFragmentSource() {
        final String source = "precision mediump float;\n" +
                "varying vec2 v_texPo;\n" +
                "uniform sampler2D s_Texture;\n" +
                "void main() {\n" +
                "   vec4 tc = texture2D(s_Texture, v_texPo);\n" +
                "   gl_FragColor = texture2D(s_Texture, v_texPo);\n" +
                "}";
        return source;
    }
}
