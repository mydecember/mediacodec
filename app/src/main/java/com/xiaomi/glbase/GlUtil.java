package com.xiaomi.glbase;

import android.graphics.Bitmap;
import android.opengl.GLES30;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class GlUtil {
    public static int mWidht = 1280;
    public static int mHeight = 720;
    public static int mPictureRotation = 0;

    public static int createProgram(String vertexSource, String fragmentSource) {

        int program = GLES30.glCreateProgram();
        checkGlError("glCreateProgram fail");

        int vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource);

        GLES30.glAttachShader(program, vertexShader);
        checkGlError("glAttachVertexShader fail");
        GLES30.glAttachShader(program, fragmentShader);
        checkGlError("glAttachFragmentShader fail");
        GLES30.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES30.GL_TRUE) {
            GLES30.glDeleteProgram(program);
            throw new RuntimeException("Could not link program");
        }

        GLES30.glDeleteShader(vertexShader);
        GLES30.glDeleteShader(fragmentShader);
        return program;
    }

    public static int loadShader(int shaderType, String source) {
        int shader = GLES30.glCreateShader(shaderType);
        checkGlError("glCreateShader fail, type = " + shaderType);
        GLES30.glShaderSource(shader, source);
        GLES30.glCompileShader(shader);
        int[] complied = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, complied, 0);
        if (complied[0] == 0) {
            GLES30.glDeleteShader(shader);
            throw new RuntimeException("glCompileShader fail");
        }
        return shader;
    }

    public static int genTextureId(int target) {
        int[] textures = new int[1];
        GLES30.glGenTextures(1, textures, 0);
        GlUtil.checkGlError("glGenTextures");

        int texId = textures[0];
        GLES30.glBindTexture(target, texId);
        GlUtil.checkGlError("glBindTexture");

        GLES30.glTexParameterf(target, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameterf(target, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GlUtil.checkGlError("glTexParameter");

        GLES30.glBindTexture(target, 0);

        return texId;
    }

    public static void checkGlError(String op) {
        int error = GLES30.glGetError();
        if (error != GLES30.GL_NO_ERROR) {
            throw new RuntimeException(op + " :0x" + error);
        }
    }

    public static void checkLocation(int location, String label) {
        if (location < 0) {
            throw new RuntimeException("Unable to locate '" + label + "' in program");
        }
    }

    public static File  saveFile(Bitmap bm, String path, String fileName){
        File dirFile = new File(path);
        if(!dirFile.exists()){
            dirFile.mkdir();
        }
        File myCaptureFile = new File(path , fileName);
        try {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(myCaptureFile));
            bm.compress(Bitmap.CompressFormat.JPEG, 80, bos);
            bos.flush();
            bos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return myCaptureFile;
    }
}