package com.xiaomi.transfer.smooth;

import android.opengl.GLES30;
import android.util.Log;

import com.xiaomi.transfer.BaseRenderDrawer;
import com.xiaomi.glbase.GlUtil;

public class BeautySmoothDrawer extends BaseRenderDrawer {
    private final static String TAG = "BeautySmoothDrawer";
    private int mInputTextureId;
    private int avPosition;
    private int afPosition;
    private int sTexture;

 //   private int smoothStepFactorLocation;
//    private int filterSizeLocation;
//    private int directionLocation;
//    private int spatialWeightTableLocation;
//    private int direction;
    // private GLFrameBuffer mFbo;
    private GaussianBlur mVerticalGaussianDrawer;
    private GaussianBlur mHorizontalGaussianDrawer;
    private RgbToYuv mRgbToYuvDrawer;

    private float[] spatialWeightTable;
    private int mWidthLocation;
    private int mHeightLocation;
    private int mWidthStrideLocation;
    private int mSmoothStepFactorLocation;
    private int mVideoFilterIntensityLocation;
    private int mSmoothLevelLocation;
    private int mSmoothComplexityLocation;
    private int mWhiteLevelLocation;
    private int mSkinRedLevelLocation;
    private int mVideoFilterLocation;
    private int mGuassianResultLocation;
    private int mYuv2rgbResultLocation;

    @Override
    public void setInputTextureId(int textureId) {
        mInputTextureId = textureId;
    }

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

        mWidthLocation = GLES30.glGetUniformLocation(mProgram, "width");
        GlUtil.checkGlError("width");
        mHeightLocation = GLES30.glGetUniformLocation(mProgram, "height");
        GlUtil.checkGlError("height");

        mWidthStrideLocation  = GLES30.glGetUniformLocation(mProgram, "width_stride");
        GlUtil.checkGlError("width11");
        mSmoothStepFactorLocation = GLES30.glGetUniformLocation(mProgram, "smoothStepFactor");
        GlUtil.checkGlError("width22");
        mVideoFilterIntensityLocation = GLES30.glGetUniformLocation(mProgram, "videoFilterIntensity");
        GlUtil.checkGlError("width33");
        mSmoothLevelLocation = GLES30.glGetUniformLocation(mProgram, "smoothLevel");
        GlUtil.checkGlError("width4");
        mSmoothComplexityLocation = GLES30.glGetUniformLocation(mProgram, "smoothComplexity");
        GlUtil.checkGlError("width5");
        mWhiteLevelLocation = GLES30.glGetUniformLocation(mProgram, "whiteLevel");
        GlUtil.checkGlError("width6");
        mSkinRedLevelLocation = GLES30.glGetUniformLocation(mProgram, "skinRedLevel");
        GlUtil.checkGlError("width7");
        mVideoFilterLocation = GLES30.glGetUniformLocation(mProgram, "videoFilter");
        GlUtil.checkGlError("width8");
        mGuassianResultLocation = GLES30.glGetUniformLocation(mProgram, "guassian_result");
        GlUtil.checkGlError("width9");
        mYuv2rgbResultLocation = GLES30.glGetUniformLocation(mProgram, "yuv2rgb_result");
        GlUtil.checkGlError("width10");


        mRgbToYuvDrawer = new RgbToYuv();
        mVerticalGaussianDrawer = new GaussianBlur(0);
        mHorizontalGaussianDrawer = new GaussianBlur(1);

        int textureId = mInputTextureId;
        mRgbToYuvDrawer.create();
        mRgbToYuvDrawer.setInputTextureId(textureId);
        mRgbToYuvDrawer.surfaceChangedSize(width, height);
        textureId = mRgbToYuvDrawer.getOutputTextureId();

        mVerticalGaussianDrawer.create();
        mVerticalGaussianDrawer.setInputTextureId(textureId);
        mVerticalGaussianDrawer.surfaceChangedSize(width, height);
        textureId = mVerticalGaussianDrawer.getOutputTextureId();

        mHorizontalGaussianDrawer.create();
        mHorizontalGaussianDrawer.setInputTextureId(textureId);
        mHorizontalGaussianDrawer.surfaceChangedSize(width, height);
        textureId = mHorizontalGaussianDrawer.getOutputTextureId();


    }

//          "uniform sampler2D fliter_y, fliter_u, fliter_v;\n" +
//                  "uniform float width, height, width_stride, smoothStepFactor;\n" +
//                  "uniform float saturation, saturationVar, lightness, averageLuminance, smoothParams[3], videoFilterIntensity;\n" +
//                  "uniform int smoothLevel, smoothComplexity, whiteLevel, skinRedLevel, videoFilter;\n" +
//                  "uniform float redCoeff[5], greenCoeff[5], blueCoeff[5];\n" +
//                  "\n" +
//                  "uniform sampler2D guassian_result;\n" +
//                  "uniform sampler2D yuv2rgb_result;\n" +


    private void onUpdateImageProperty() {
        GLES30.glUniform1f(mWidthLocation, width);
        GlUtil.checkGlError("direction width");
        GLES30.glUniform1f(mHeightLocation, height);
        GlUtil.checkGlError("direction height ");
        GLES30.glUniform1f(mWidthStrideLocation, width);
        GlUtil.checkGlError("direction mWidthStrideLocation");
        GLES30.glUniform1f(mSmoothStepFactorLocation, 1.0f);
        GlUtil.checkGlError("direction factor");
        GLES30.glUniform1f(mVideoFilterIntensityLocation, 1.0f);
        GlUtil.checkGlError("mSmoothStepFactorLocation size");
        GLES30.glUniform1i(mSmoothLevelLocation, 6);
        GlUtil.checkGlError("mSmoothLevelLocation size");
        GLES30.glUniform1i(mSmoothComplexityLocation, 9);
        GlUtil.checkGlError("mSmoothComplexityLocation size");
        GLES30.glUniform1i(mWhiteLevelLocation, 3);
        GlUtil.checkGlError("mWhiteLevelLocation size");
        GLES30.glUniform1i(mSkinRedLevelLocation, 0);
        GlUtil.checkGlError("mSkinRedLevelLocation size");
        GLES30.glUniform1i(mVideoFilterLocation, 0);
        GlUtil.checkGlError("mVideoFilterLocation size");
    }

    public void draw(long timestamp, float[] transformMatrix){

        mRgbToYuvDrawer.draw(timestamp, transformMatrix);
        mVerticalGaussianDrawer.draw(timestamp, transformMatrix);
        mHorizontalGaussianDrawer.draw(timestamp, transformMatrix);

        int checkBufferStatus = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
        GlUtil.checkGlError("check the completeness status fo the framebuffer failed.\n");
        if (checkBufferStatus != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.i(TAG, " status no frame glCheckFramebufferStatus " +checkBufferStatus);
        }


        useProgram();
        GlUtil.checkGlError("glUseProgram ");
        mFbo.bind();
        clear();
        viewPort(0, 0, width, height);
        onDraw();
        GLES30.glFlush();
        mFbo.unBind();
        GlUtil.checkGlError("unBind error");
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

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mHorizontalGaussianDrawer.getOutputTextureId());
        GLES30.glUniform1i(mGuassianResultLocation, 1);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE2);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mRgbToYuvDrawer.getOutputTextureId());
        GLES30.glUniform1i(mYuv2rgbResultLocation, 2);

        //绘制 GLES30.GL_TRIANGLE_STRIP:复用坐标
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, VertexCount);
        GLES30.glDisableVertexAttribArray(avPosition);
        GLES30.glDisableVertexAttribArray(afPosition);
    }

    @Override
    public void release() {
        if (mVerticalGaussianDrawer != null)
            mVerticalGaussianDrawer.release();
        if (mHorizontalGaussianDrawer != null)
            mHorizontalGaussianDrawer.release();
        if (mRgbToYuvDrawer != null)
            mRgbToYuvDrawer.release();
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
                "uniform sampler2D fliter_y, fliter_u, fliter_v;\n" +
                "uniform float width, height, width_stride, smoothStepFactor;\n" +
                "uniform float saturation, saturationVar, lightness, averageLuminance, smoothParams[3], videoFilterIntensity;\n" +
                "uniform int smoothLevel, smoothComplexity, whiteLevel, skinRedLevel, videoFilter;\n" +
                "uniform float redCoeff[5], greenCoeff[5], blueCoeff[5];\n" +
                "\n" +
                "uniform sampler2D guassian_result;\n" +
                "uniform sampler2D yuv2rgb_result;\n" +
                "  \n" +
                "\n" +
                "\n" +
                "\n" +
                "\n" +
                "void main (void)\n" +
                "{\n" +
                "    float r, g, b, y, u, v, a;\n" +
                "    vec4 smoothed_rgb;\n" +
                "    vec4 rgbdata;\n" +
                "    vec4 rgbdatatemp = vec4(1.0);\n" +
                "    // width - 1.0 instead of width to avoid one pixel off issue.\n" +
                "    vec2 pos = varTexcoord.xy;// * vec2((width - 1.0)/width_stride, 1.0);\n" +
                "    \n" +
                "    \n" +
                "    highp vec4 finalColor =  texture2D(yuv2rgb_result, pos, 0.0);\n" +
                "    y = finalColor.r;\n" +
                "    \n" +
                "    float skin = finalColor.a;\n" +
                "    if(smoothComplexity != 0)\n" +
                "    {\n" +
                "        smoothed_rgb = texture2D(guassian_result, pos / smoothStepFactor, 0.0);\n" +
                "        skin = smoothed_rgb.a;\n" +
                "    }\n" +
                "    //begin smooth process\n" +
                "    if (smoothComplexity != 0 && smoothLevel != 0) {\n" +
                "        // ----------------smooth--------------------------\n" +
                "        float refAvg = smoothed_rgb.r;\n" +
                "        float highPass = y - refAvg + 0.5;\n" +
                "        highPass = clamp(highPass, 0.0, 1.0);\n" +
                "        if (highPass <= 0.5) {\n" +
                "            highPass = pow(highPass * 2.0, 20.0) / 2.0;\n" +
                "        } else {\n" +
                "            highPass = 1.0 - highPass;\n" +
                "            highPass = pow(highPass * 2.0, 20.0) / 2.0;\n" +
                "            highPass = 1.0 - highPass;\n" +
                "        }\n" +
                "        float alphaParam = refAvg * skin;\n" +
                "        finalColor.r = finalColor.r + (finalColor.r - highPass) * alphaParam * 0.12 * float(smoothLevel) / 100.0;\n" +
                "        finalColor = clamp(finalColor, vec4(0.0), vec4(1.0));\n" +
                "    }\n" +
                "\n" +
                "    // white face process ---- light -----\n" +
                "    float temp = log(1.7 * finalColor.r + 1.0)/log(2.7);// = 0.431\n" +
                "    finalColor.r = mix(finalColor.r, temp, 0.5 * float(whiteLevel) / 100.0);\n" +
                "    if(finalColor.r < 0.4){\n" +
                "        finalColor.r = 0.9 * finalColor.r + 0.4 - 0.4 * 0.9;\n" +
                "    }\n" +
                "    if (videoFilter == 1) {//black\n" +
                "        finalColor.rgb = vec3(finalColor.r, 0.5, 0.5);\n" +
                "    }\n" +
                "    else// red or video filter need yuv2rgb\n" +
                "    {\n" +
                "        rgbdata.r = clamp(1.1643 * (finalColor.r - 0.0625) + 1.5958  * (finalColor.b - 0.5), 0.0, 1.0);\n" +
                "        rgbdata.g = clamp(1.1643 * (finalColor.r - 0.0625) - 0.39173 * (finalColor.g - 0.5) - 0.81290 * (finalColor.b - 0.5), 0.0, 1.0);\n" +
                "        rgbdata.b = clamp(1.1643 * (finalColor.r - 0.0625) + 2.017   * (finalColor.g - 0.5), 0.0, 1.0);\n" +
                "        // redface\n" +
                "        if(skinRedLevel > 0)\n" +
                "        {\n" +
                "            float quadx, quady, x, y;\n" +
                "            float bi = floor(rgbdata.b * 15.0);\n" +
                "            float mixratio = rgbdata.b * 15.0 - floor(rgbdata.b * 15.0);\n" +
                "\n" +
                "            quady = floor(bi / 4.0);\n" +
                "            quadx = bi - quady * 4.0;\n" +
                "            x = quadx * 16.0 + clamp(rgbdata.r * 15.0, 1.0, 14.0);\n" +
                "            y = quady * 16.0 + clamp(rgbdata.g * 15.0, 1.0, 14.0);\n" +
                "            vec2 poss1 = vec2(x / 64.0, y / 64.0);\n" +
                "\n" +
                "            bi = bi + 1.0;\n" +
                "            quady = floor(bi / 4.0);\n" +
                "            quadx = bi - quady * 4.0;\n" +
                "            x = quadx * 16.0 + clamp(rgbdata.r * 15.0, 1.0, 14.0);\n" +
                "            y = quady * 16.0 + clamp(rgbdata.g * 15.0, 1.0, 14.0);\n" +
                "            vec2 poss2 = vec2(x / 64.0, y / 64.0);\n" +
                "\n" +
                "            rgbdatatemp.r = mix(texture2D(fliter_y, poss1, 0.0).r, texture2D(fliter_y, poss2, 0.0).r, mixratio);\n" +
                "            rgbdatatemp.g = mix(texture2D(fliter_u, poss1, 0.0).r, texture2D(fliter_u, poss2, 0.0).r, mixratio);\n" +
                "            rgbdatatemp.b = mix(texture2D(fliter_v, poss1, 0.0).r, texture2D(fliter_v, poss2, 0.0).r, mixratio);\n" +
                "            rgbdata = mix(rgbdata, rgbdatatemp, float(skinRedLevel) / 100.0);\n" +
                "        }\n" +
                "    // video filter preprocess\n" +
                "        if(videoFilter > 1) \n" +
                "        {\n" +
                "            rgbdata.r = mix(rgbdata.r, rgbdata.r * (((redCoeff[0] * rgbdata.r + redCoeff[1]) * rgbdata.r + redCoeff[2]) * rgbdata.r + redCoeff[3]) + redCoeff[4], videoFilterIntensity);\n" +
                "            rgbdata.g = mix(rgbdata.g, rgbdata.g * (((greenCoeff[0] * rgbdata.g + greenCoeff[1]) * rgbdata.g + greenCoeff[2]) * rgbdata.g + greenCoeff[3]) + greenCoeff[4], videoFilterIntensity);\n" +
                "            rgbdata.b = mix(rgbdata.b, rgbdata.b * (((blueCoeff[0] * rgbdata.b + blueCoeff[1]) * rgbdata.b + blueCoeff[2]) * rgbdata.b + blueCoeff[3]) + blueCoeff[4], videoFilterIntensity);\n" +
                "        }\n" +
                "        \n" +
                "        mat4 RGB2YUV = mat4( 0.257,  0.504,  0.098, 0.0625,\n" +
                "                            -0.148, -0.291,  0.439, 0.500,\n" +
                "                             0.439, -0.368, -0.071, 0.500,\n" +
                "                             0.000,  0.000,  0.000, 1.000);\n" +
                "        rgbdata.a = 1.0;\n" +
                "        finalColor = clamp(rgbdata * RGB2YUV, vec4(0.0), vec4(1.0));\n" +
                "    }\n" +
                "\n" +
                "    //gl_FragColor = finalColor;\n" +
                "    y = finalColor.r;" +
                "    u = finalColor.g;" +
                "    v = finalColor.b;" +
                "    r = clamp(1.1643 * (y - 0.0625) + 1.5958  * (v - 0.5), 0.0, 1.0);\n" +
                "    g = clamp(1.1643 * (y - 0.0625) - 0.39173 * (u - 0.5) - 0.81290 * (v - 0.5), 0.0, 1.0);\n" +
                "    b = clamp(1.1643 * (y - 0.0625) + 2.017   * (u - 0.5), 0.0, 1.0);\n"+
                "    gl_FragColor = vec4(r, g, b, finalColor.a);\n" +
                "}\n" +
                "\n";
        return source;
    }
}
