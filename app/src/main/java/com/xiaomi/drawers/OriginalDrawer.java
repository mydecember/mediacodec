package com.xiaomi.drawers;

import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.opengl.Matrix;
import android.util.Log;

import com.xiaomi.glbase.GlUtil;

public class OriginalDrawer extends BaseDrawer {
    private int av_Position;
    private int af_Position;
    private int s_Texture;
    private int s_mvp;
    private int mInputTextureId;
    private int mOutputTextureId;

    private final float[] modelMatrix = new float[16];  //获得一个model矩阵


    @Override
    protected void onCreated() {
    }
    @Override
    public void release() {
        super.release();
    }
    @Override
    protected void onChanged(int width, int height) {
        //mOutputTextureId = GlesUtil.createFrameTexture(width, height);

        av_Position = GLES30.glGetAttribLocation(mProgram, "av_Position");
        af_Position = GLES30.glGetAttribLocation(mProgram, "af_Position");
        s_Texture = GLES30.glGetUniformLocation(mProgram, "s_Texture");
        s_mvp = GLES30.glGetUniformLocation(mProgram, "modelViewProjectionMatrix");
    }

    @Override
    protected void onDraw() {
        if (mInputTextureId == 0 ){//|| mOutputTextureId == 0) {
            Log.i("TAG", "not inited");
            return;
        }
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.rotateM(modelMatrix, 0, GlUtil.mPictureRotation, 0f, 0f, 1f);//绕着Z轴旋转rotateAngle

        GLES30.glUniformMatrix4fv(s_mvp,1, false, modelMatrix, 0);
        GLES30.glEnableVertexAttribArray(av_Position);
        GLES30.glEnableVertexAttribArray(af_Position);
        //GLES30.glVertexAttribPointer(av_Position, CoordsPerVertexCount, GLES30.GL_FLOAT, false, VertexStride, mVertexBuffer);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mVertexBufferId);
        GLES30.glVertexAttribPointer(av_Position, CoordsPerVertexCount, GLES30.GL_FLOAT, false, 0, 0);
        //if (CameraUtil.isBackCamera()) {
        //GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mBackTextureBufferId);
        //} else {
        //GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mFrontTextureBufferId);
        //}
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mFrameTextureBufferId);

        GLES30.glVertexAttribPointer(af_Position, CoordsPerTextureCount, GLES30.GL_FLOAT, false, 0, 0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        bindTexture(mInputTextureId);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, VertexCount);
        unBindTexure();
        GLES30.glDisableVertexAttribArray(av_Position);
        GLES30.glDisableVertexAttribArray(af_Position);
        GLES30.glFlush();
    }

    private void bindTexture(int textureId) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES30.glUniform1i(s_Texture, 0);
    }

    private void unBindTexure() {
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
    }

    @Override
    public void setInputTextureId(int textureId) {
        mInputTextureId = textureId;
    }

//    @Override
//    public int getOutputTextureId() {
//        return mOutputTextureId;
//    }

    @Override
    protected String getVertexSource() {
        final String source = "attribute vec4 av_Position; " +
                "attribute vec2 af_Position; " +
                "varying vec2 v_texPo; " +
                "uniform mat4 modelViewProjectionMatrix;" +
                "void main() { " +
                "    v_texPo = af_Position; " +
                "    gl_Position = modelViewProjectionMatrix * av_Position; " +
                "}";
        return source;
    }

    @Override
    protected String getFragmentSource() {
        final String source = "#extension GL_OES_EGL_image_external : require \n" +
                "precision mediump float; " +
                "varying vec2 v_texPo; " +
                "uniform samplerExternalOES s_Texture; " +
                "void main() { " +
                "   gl_FragColor = texture2D(s_Texture, v_texPo); " +
                "} ";
        return source;
    }
}
