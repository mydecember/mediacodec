package com.xiaomi.transfer.smooth;

import android.opengl.GLES30;
import android.util.Log;

import com.xiaomi.transfer.BaseRenderDrawer;
import com.xiaomi.transfer.GlUtil;

public class GaussianBlur extends BaseRenderDrawer {
    private int mInputTextureId;
    private int avPosition;
    private int afPosition;
    private int sTextureLocation;
    private int widthLocation;
    private int heightLocation;
    private int smoothStepFactorLocation;
    private int filterSizeLocation;
    private int directionLocation;
    private int spatialWeightTableLocation;
    private int direction;

    private float[] spatialWeightTable;
    public  GaussianBlur(int direct) {
        this.direction = direct;
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
        avPosition = GLES30.glGetAttribLocation(mProgram, "av_Position");
        GlUtil.checkGlError("get av position");
        afPosition = GLES30.glGetAttribLocation(mProgram, "af_Position");
        GlUtil.checkGlError("get af position");
        sTextureLocation = GLES30.glGetUniformLocation(mProgram, "input_texture");
        GlUtil.checkGlError("input texture");
        widthLocation = GLES30.glGetUniformLocation(mProgram, "width");
        GlUtil.checkGlError("width");
        heightLocation = GLES30.glGetUniformLocation(mProgram, "height");
        GlUtil.checkGlError("height");
        smoothStepFactorLocation = GLES30.glGetUniformLocation(mProgram, "smoothStepFactor");
        GlUtil.checkGlError("smoothStepFactor");
        filterSizeLocation = GLES30.glGetUniformLocation(mProgram, "filterSize");
        GlUtil.checkGlError("filterSize");
        directionLocation = GLES30.glGetUniformLocation(mProgram, "direction");
        GlUtil.checkGlError("direction");
        spatialWeightTableLocation =  GLES30.glGetUniformLocation(mProgram, "spatialWeightTable");
        GlUtil.checkGlError("table");
    }

    private void onUpdateImageProperty() {
        GLES30.glUniform1f(widthLocation, width);
        GlUtil.checkGlError("direction width");
        GLES30.glUniform1f(heightLocation, height);
        GlUtil.checkGlError("direction height ");
        GLES30.glUniform1f(smoothStepFactorLocation, 1.0f);
        GlUtil.checkGlError("direction factor");
        GLES30.glUniform1i(directionLocation, 0);
        GlUtil.checkGlError("direction direction");
        GLES30.glUniform1i(filterSizeLocation, 9);
        GlUtil.checkGlError("direction size");

       // GLES30.glUniform1fv(spatialWeightTableLocation, spatialWeightTable);
        GLES30.glUniform1fv(spatialWeightTableLocation, 16, spatialWeightTable, 0);
        GlUtil.checkGlError("direction size");
    }

    @Override
    protected void onDraw() {

        GLES30.glDisable(GLES30.GL_BLEND);
        GlUtil.checkGlError("GL_BLEND");
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GlUtil.checkGlError("GL_CULL_FACE");
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GlUtil.checkGlError("GL_DEPTH_TEST");
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        GlUtil.checkGlError("GL_COLOR_BUFFER_BIT");

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
        GLES30.glUniform1i(sTextureLocation, 0);
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
        final String source = "attribute vec4 av_Position; \n" +
                "attribute vec2 af_Position; \n" +
                "varying vec2 varTexcoord; \n" +
                "void main() { \n" +
                "    varTexcoord = af_Position; \n" +
                "    gl_Position = av_Position; \n" +
                "}\n" +
                "\n";
        return source;
    }

    @Override
    protected String getFragmentSource() {
        final String source = "precision highp float; \n" +
                "varying vec2 varTexcoord; \n" +
                "\n" +
                "uniform float width, height, width_stride, smoothStepFactor; \n" +
                "uniform int filterSize, direction; // 0 for horizental 1 for vertical \n" +
                "uniform float spatialWeightTable[16]; \n" +
                "\n" +
                "uniform sampler2D input_texture; \n" +
                "\n" +
                "const int MAX_SPATIAL_DISTANCE = 16; \n" +
                "const int SPATIAL_DISTANCE_OFFSET = MAX_SPATIAL_DISTANCE / 2; \n" +
                "const int MAX_SPATIAL_DISTANCE_LOG = 4; \n" +
                "\n" +
                "#define ADD_WEIGHT_PIXEL_X(index, weightIndex) \\\n" +
                "   { \\\n" +
                "       int deltaX = int(index) - range; \\\n" +
                "       refPos= pos + vec2(0.0, refStep.y) * float(deltaX); \\\n" +
                "       pixelColor = texture2D(input_texture, refPos); \\\n" +
                "       float dd = pixelColor.r - pixCur.r;\\\n" +
                "       float ww = exp(-dd * dd * 30.0);\\\n" +
                "       pixelWeight = vec2( ww * spatialWeightTable[weightIndex] , 1.0);\\\n" +
                "       pixelWeightSum += pixelWeight;\\\n" +
                "       finalColor.ra += pixelWeight * pixelColor.ra; \\\n" +
                "   }\n" +
                "\n" +
                "#define ADD_WEIGHT_PIXEL_Y(index, weightIndex) \\\n" +
                "   { \\\n" +
                "       int deltaX = int(index) - range; \\\n" +
                "       refPos= pos + vec2(refStep.x, 0.0) * float(deltaX);; \\\n" +
                "       pixelColor = texture2D(input_texture, refPos); \\\n" +
                "       float dd = pixelColor.r - pixCur.r ;\\\n" +
                "       float ww = exp(-dd * dd * 30.0);\\\n" +
                "       pixelWeight = vec2( ww * spatialWeightTable[weightIndex] , 1.0);\\\n" +
                "       pixelWeightSum += pixelWeight;\\\n" +
                "       finalColor.ra += pixelWeight * pixelColor.ra; \\\n" +
                "   }\n" +
                "\n" +
                "void main (void)\n" +
                "{ \n" +
                "    vec2 outpos = varTexcoord.xy;\n" +
                "    highp vec4 finalColor = vec4(0.0);\n" +
                "\n" +
                "    vec2 refPos = vec2(0.0);\n" +
                "    vec4 pixelColor = vec4(0.0);\n" +
                "    vec2 pixelWeight = vec2(0.0);\n" +
                "    int range = (filterSize  - 1) / 2; \n" +
                "    vec2 pixelWeightSum = vec2(0.0); \n" +
                "\n" +
                "    if(outpos.x < (1.0 / smoothStepFactor) && outpos.y < (1.0 / smoothStepFactor))\n" +
                "    {\n" +
                "        if (direction == 0) {\n" +
                "                // Ideally we should support any odd filter size and write this as an loop.\n" +
                "                // but on Mi4 there is an bug, seams for(int index = 0; ...), the index are treat as <unsigned int> instead of <int>.\n" +
                "                vec2 pos = outpos;\n" +
                "                vec4 pixCur = texture2D(input_texture, pos);\n" +
                "                vec2 refStep = vec2(1.0 / width, 1.0 / height);\n" +
                "\n" +
                "                int tmp = SPATIAL_DISTANCE_OFFSET - range;\n" +
                "                ADD_WEIGHT_PIXEL_X(0, 0 + tmp);\n" +
                "                ADD_WEIGHT_PIXEL_X(1, 1 + tmp);\n" +
                "                ADD_WEIGHT_PIXEL_X(2, 2 + tmp);\n" +
                "                ADD_WEIGHT_PIXEL_X(3, 3 + tmp);\n" +
                "                ADD_WEIGHT_PIXEL_X(4, 4 + tmp);\n" +
                "                if (range > 2) {\n" +
                "                    ADD_WEIGHT_PIXEL_X(5, 5 + tmp);\n" +
                "                    ADD_WEIGHT_PIXEL_X(6, 6 + tmp);\n" +
                "                    ADD_WEIGHT_PIXEL_X(7, 7 + tmp);\n" +
                "                    ADD_WEIGHT_PIXEL_X(8, 8 + tmp);\n" +
                "                }\n" +
                "        } else {\n" +
                "                vec2 pos = outpos * smoothStepFactor;\n" +
                "                vec4 pixCur = texture2D(input_texture, pos);\n" +
                "                vec2 refStep = vec2(smoothStepFactor / width, smoothStepFactor / height);\n" +
                "\n" +
                "                int tmp = SPATIAL_DISTANCE_OFFSET - range;\n" +
                "                ADD_WEIGHT_PIXEL_Y(0, 0 + tmp);\n" +
                "                ADD_WEIGHT_PIXEL_Y(1, 1 + tmp);\n" +
                "                ADD_WEIGHT_PIXEL_Y(2, 2 + tmp);\n" +
                "                ADD_WEIGHT_PIXEL_Y(3, 3 + tmp);\n" +
                "                ADD_WEIGHT_PIXEL_Y(4, 4 + tmp);\n" +
                "                if (range > 2) {\n" +
                "                    ADD_WEIGHT_PIXEL_Y(5, 5 + tmp);\n" +
                "                    ADD_WEIGHT_PIXEL_Y(6, 6 + tmp);\n" +
                "                    ADD_WEIGHT_PIXEL_Y(7, 7 + tmp);\n" +
                "                    ADD_WEIGHT_PIXEL_Y(8, 8 + tmp);\n" +
                "                }\n" +
                "        }\n" +
                "        finalColor.ra /= pixelWeightSum;\n" +
                "    }\n" +
                "\n" +
                "   float y, u, v;\n" +
                " //finalColor = texture2D(input_texture, varTexcoord);\n"+
                "    y = finalColor.r;" +
                "    u = finalColor.g;" +
                "    v = finalColor.b;" +
                "    float r = clamp(1.1643 * (y - 0.0625) + 1.5958  * (v - 0.5), 0.0, 1.0);\n" +
                "    float g = clamp(1.1643 * (y - 0.0625) - 0.39173 * (u - 0.5) - 0.81290 * (v - 0.5), 0.0, 1.0);\n" +
                "    float b = clamp(1.1643 * (y - 0.0625) + 2.017   * (u - 0.5), 0.0, 1.0);\n"+
                "    gl_FragColor = finalColor;//vec4(r, g, b, finalColor.a);\n" +
                "}\n" +
                "\n";
        return source;
    }

}
