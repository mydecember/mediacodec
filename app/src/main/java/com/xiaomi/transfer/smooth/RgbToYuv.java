package com.xiaomi.transfer.smooth;

import android.opengl.GLES30;
import android.util.Log;

import com.xiaomi.transfer.BaseRenderDrawer;
import com.xiaomi.glbase.GlUtil;

public class RgbToYuv extends BaseRenderDrawer {
    private int mInputTextureId;
    private int avPosition;
    private int afPosition;
    private int sTexture;
    private int width_pos;
    private int height_pos;
    private int smoothStepFactorLocation;
    private int filterSizeLocation;
    private int directionLocation;
    private int spatialWeightTableLocation;
    private int direction;
   // private GLFrameBuffer mFbo;

    private float[] spatialWeightTable;
    public  RgbToYuv() {
        //mFbo = new GLFrameBuffer();
    }
    @Override
    public void setInputTextureId(int textureId) {
        mInputTextureId = textureId;
    }

//    @Override
//    public int getOutputTextureId() {
//        return mInputTextureId;
//    }

    static float Gaussian(float theta, float delta) {
        return (float) Math.exp( -1 * delta * delta / 2 /(theta * theta));
    }

    @Override
    protected void onCreated() {
        int MAX_SPATIAL_DISTANCE = 16;
        float totalWeight = 0;
        int m_filter_size = 9;
        int offset = MAX_SPATIAL_DISTANCE  / 2;
        float theta = m_filter_size / 3.0f;

        spatialWeightTable = new float[MAX_SPATIAL_DISTANCE];

        for (int i = 0; i < MAX_SPATIAL_DISTANCE; i++) {
            spatialWeightTable[i] = 0.0f;
        }

        for (int deltaX = -1 * (m_filter_size - 1) / 2; deltaX <= (m_filter_size - 1) / 2; deltaX++) {
            int index = deltaX + offset;
            float value = Gaussian(theta, deltaX);
            spatialWeightTable[index] = value;
            totalWeight += spatialWeightTable[deltaX + offset];
        }

        for (int i = 0; i < MAX_SPATIAL_DISTANCE; i++) {
            spatialWeightTable[i] /= totalWeight;
        }

        for (int i =0; i < MAX_SPATIAL_DISTANCE; i++) {
            Log.i("1111", "Generate the gaussian table, filter size:" + m_filter_size  + " value:" + spatialWeightTable[i] + " index:" +i);
        }

    }

    @Override
    protected void onChanged(int width, int height) {
        //mFbo.initiate(width, height, 0);
        avPosition = GLES30.glGetAttribLocation(mProgram, "av_Position");
        GlUtil.checkGlError("get av position");
        afPosition = GLES30.glGetAttribLocation(mProgram, "af_Position");
        GlUtil.checkGlError("get af position");
        sTexture = GLES30.glGetUniformLocation(mProgram, "input_texture");
        GlUtil.checkGlError("input texture");
    }

    private void onUpdateImageProperty() {

    }

    @Override
    protected void onDraw() {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        onUpdateImageProperty();

        GLES30.glEnableVertexAttribArray(avPosition);
        GLES30.glEnableVertexAttribArray(afPosition);
        //设置顶点位置值
        //GLES30.glVertexAttribPointer(avPosition, CoordsPerVertexCount, GLES30.GL_FLOAT, false, VertexStride, mVertexBuffer);
        //设置纹理位置值
        //GLES30.glVertexAttribPointer(afPosition, CoordsPerTextureCount, GLES30.GL_FLOAT, false, TextureStride, mFrameTextureBuffer);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mVertexBufferId);
        GLES30.glVertexAttribPointer(avPosition, CoordsPerVertexCount, GLES30.GL_FLOAT, false, 0, 0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mFrameTextureBufferId);
        GLES30.glVertexAttribPointer(afPosition, CoordsPerTextureCount, GLES30.GL_FLOAT, false, 0, 0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mInputTextureId);
        GLES30.glUniform1i(sTexture, 0);
        //绘制 GLES30.GL_TRIANGLE_STRIP:复用坐标
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, VertexCount);
        GLES30.glDisableVertexAttribArray(avPosition);
        GLES30.glDisableVertexAttribArray(afPosition);
    }

    @Override
    public void release() {
        super.release();
    }


    @Override
    protected String getVertexSource() {
        final String source = "attribute vec4 av_Position; " +
                "attribute vec2 af_Position; " +
                "varying vec2 varTexcoord; " +
                "void main() { " +
                "    varTexcoord = af_Position; " +
                "    gl_Position = av_Position; " +
                "}";
        return source;
    }

    @Override
    protected String getFragmentSource() {
        final String source = "precision highp float;\n" +
                "varying vec2 varTexcoord;\n" +
                "uniform sampler2D input_texture;\n" +
                "\n"+
                "float skindetect( float u, float v)\n" +
                "{\n" +
                "    vec2 skin_cb_cr = vec2(u * 255.0, v * 255.0);\n" +
                "    skin_cb_cr = skin_cb_cr - vec2(102.0, 153.0);\n" +
                "    vec2 powValue = skin_cb_cr * skin_cb_cr;\n" +
                "    vec2 expValue = exp(-powValue / (vec2(625, 400)));\n" +
                "    return expValue.x * expValue.y * 3.0;\n" +
                "}\n" +
                "void main (void)\n" +
                "{\n" +
                "    float y, u, v, a;\n" +
                "    vec4 rgbdata;\n"+
                "    rgbdata = texture2D(input_texture, varTexcoord);\n" +
                "    rgbdata.a =1.0;\n" +
                "mat4 RGB2YUV = mat4(        0.257,  0.504,  0.098, 0.0625,\n" +
                "                            -0.148, -0.291,  0.439, 0.500,\n" +
                "                             0.439, -0.368, -0.071, 0.500,\n" +
                "                             0.000,  0.000,  0.000, 1.000);\n" +
                "  vec4 finalColor = clamp(rgbdata * RGB2YUV, vec4(0.0), vec4(1.0));\n" +
                " u = finalColor.g;\n"+
                " v = finalColor.b;\n"+
                "    a = skindetect(u, v);\n" +
                "\n" +
                "    //y = finalColor.r;" +
                "    //u = finalColor.g;" +
                "    //v = finalColor.b;" +
                "    //float r = clamp(1.1643 * (y - 0.0625) + 1.5958  * (v - 0.5), 0.0, 1.0);\n" +
                "    //float g = clamp(1.1643 * (y - 0.0625) - 0.39173 * (u - 0.5) - 0.81290 * (v - 0.5), 0.0, 1.0);\n" +
                "    //float b = clamp(1.1643 * (y - 0.0625) + 2.017   * (u - 0.5), 0.0, 1.0);"+

                "  finalColor.a = a;\n" +
                "  gl_FragColor = finalColor;\n" +
                "}\n";
        return source;
    }

}
