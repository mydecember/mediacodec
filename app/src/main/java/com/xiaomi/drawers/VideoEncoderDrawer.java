package com.xiaomi.drawers;
import android.opengl.GLES30;
import android.opengl.Matrix;
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
    private int s_mvp;
    private int src_width =  0;
    private int src_height = 0;
    private final float[] modelMatrix = new float[16];

    public void setSRCWidthAndHeight(int w, int h) {
        src_width = w;
        src_height = h;
    }
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
        //mProgram = GlesUtil.createProgram(getVertexSource(), getFragmentSource());
        //initVertexBufferObjects();
        av_Position = GLES30.glGetAttribLocation(mProgram, "av_Position");
        af_Position = GLES30.glGetAttribLocation(mProgram, "af_Position");
        s_Texture = GLES30.glGetUniformLocation(mProgram, "s_Texture");
        s_mvp = GLES30.glGetUniformLocation(mProgram, "modelViewProjectionMatrix");
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

        Matrix.setIdentityM(modelMatrix, 0);
        if (src_height != 0 && src_width != 0) {
            float t_h = width * src_height * 1.0f / src_width;
            float t_w = height * src_width * 1.0f / src_height ;

            if (t_h <= (float)height) {
                Matrix.scaleM(modelMatrix, 0, 1f, t_h / height, 1f);
            } else {
                Matrix.scaleM(modelMatrix, 0, t_w / width, 1f, 1f);
            }
        }

       // Matrix.scaleM(modelMatrix, 0, 1f, 0.5f, 1f);
       // Matrix.rotateM(modelMatrix, 0, 50, 0f, 0f, 1f);//绕着Z轴旋转rotateAngle
       // Matrix.translateM(modelMatrix, 0, 1.0f, 0.0f, 0f);
       // Log.i("original", " ===== " + modelMatrix);




        GLES30.glUniformMatrix4fv(s_mvp,1, false, modelMatrix, 0);

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
                "uniform mat4 modelViewProjectionMatrix;" +
                "varying vec2 v_texPo; " +
                "void main() { " +
                "    v_texPo = af_Position; " +
                "    gl_Position = modelViewProjectionMatrix * av_Position; " +
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
