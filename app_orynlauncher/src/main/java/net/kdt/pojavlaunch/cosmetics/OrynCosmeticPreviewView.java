package net.kdt.pojavlaunch.cosmetics;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import android.view.MotionEvent;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class OrynCosmeticPreviewView extends GLSurfaceView {
    private final Renderer renderer;

    public OrynCosmeticPreviewView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        renderer = new Renderer();
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    public void setSkin(Bitmap bitmap, boolean slim) {
        renderer.setSkin(bitmap, slim);
    }

    public void setCape(Bitmap bitmap) {
        renderer.setCape(bitmap);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            renderer.lastX = event.getX();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - renderer.lastX;
            renderer.rotation += dx * 0.65f;
            renderer.lastX = event.getX();
            return true;
        }
        return true;
    }

    private static final class Renderer implements GLSurfaceView.Renderer {
        private final float[] projection = new float[16];
        private final float[] view = new float[16];
        private final float[] model = new float[16];
        private final float[] mvp = new float[16];
        private final float[] tmp = new float[16];
        private final float[] light = {0.0f, 0.35f, 0.94f, 0.0f};
        private FloatBuffer vertexBuffer, uvBuffer;
        private int program, texture = 0;
        private Bitmap skin, cape;
        private Bitmap pendingSkin, pendingCape;
        private boolean slim;
        private float rotation = -18f;
        private float lastX;

        void setSkin(Bitmap b, boolean isSlim) {
            pendingSkin = b;
            slim = isSlim;
        }

        void setCape(Bitmap b) {
            pendingCape = b;
        }

        @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glClearColor(0.035f, 0.043f, 0.055f, 1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            GLES20.glCullFace(GLES20.GL_BACK);
            program = buildProgram();
        }

        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            float ratio = width / (float)Math.max(1, height);
            Matrix.frustumM(projection, 0, -ratio, ratio, -1f, 1f, 2.5f, 40f);
        }

        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
            if (pendingSkin != skin) {
                skin = pendingSkin;
                if (texture != 0) GLES20.glDeleteTextures(1, new int[]{texture}, 0);
                texture = uploadTexture(skin);
            }
            if (pendingCape != cape) cape = pendingCape;

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            Matrix.setLookAtM(view, 0, 0f, 1.2f, 7.2f, 0f, 0.3f, 0f, 0f, 1f, 0f);
            Matrix.setIdentityM(model, 0);
            Matrix.rotateM(model, 0, rotation, 0f, 1f, 0f);
            Matrix.multiplyMM(tmp, 0, view, 0, model, 0);
            Matrix.multiplyMM(mvp, 0, projection, 0, tmp, 0);

            GLES20.glUseProgram(program);
            int pos = GLES20.glGetAttribLocation(program, "aPosition");
            int uv = GLES20.glGetAttribLocation(program, "aTexCoord");
            int mat = GLES20.glGetUniformLocation(program, "uMVP");
            int sampler = GLES20.glGetUniformLocation(program, "uTexture");
            int tint = GLES20.glGetUniformLocation(program, "uTint");
            int hasTexture = GLES20.glGetUniformLocation(program, "uHasTexture");
            GLES20.glUniformMatrix4fv(mat, 1, false, mvp, 0);
            GLES20.glUniform1i(sampler, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            if (texture != 0) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
                GLES20.glUniform1i(hasTexture, 1);
                GLES20.glUniform4f(tint, 1f,1f,1f,1f);
            } else {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
                GLES20.glUniform1i(hasTexture, 0);
                GLES20.glUniform4f(tint, 0.56f,0.59f,0.64f,1f);
            }

            drawCube(pos, uv, 0f, 2.35f, 0f, 0.9f, 0.9f, 0.9f, 8, 8, 8, 8);
            drawCube(pos, uv, 0f, 0.72f, 0f, 1.55f, 1.8f, 0.85f, 20, 20, 8, 12);
            float arm = slim ? 0.34f : 0.42f;
            drawCube(pos, uv, -1.03f, 0.78f, 0f, arm, 1.7f, 0.85f, 44, 20, 4, 12);
            drawCube(pos, uv,  1.03f, 0.78f, 0f, arm, 1.7f, 0.85f, 44, 20, 4, 12);
            drawCube(pos, uv, -0.48f, -1.05f, 0f, 0.46f, 1.75f, 0.9f, 4, 20, 4, 12);
            drawCube(pos, uv,  0.48f, -1.05f, 0f, 0.46f, 1.75f, 0.9f, 20, 20, 4, 12);

            if (cape != null) drawCape(pos, uv, tint, 0f, 0.85f, -0.62f, 1.5f, 1.9f);
        }

        private void drawCape(int pos, int uv, int tint, float x, float y, float z, float w, float h) {
            // A thin textured plane behind the torso. Cape texture uses its native image as a single panel.
            if (cape == null) return;
            int capeTex = uploadTexture(cape);
            if (capeTex == 0) return;
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, capeTex);
            GLES20.glUniform4f(tint, 1f,1f,1f,1f);
            float[] v = {-w/2, y+h/2, z,  w/2,y+h/2,z,  w/2,y-h/2,z, -w/2,y-h/2,z};
            float[] t = {0,0, 1,0, 1,1, 0,1};
            drawQuad(pos, uv, v, t);
            GLES20.glDeleteTextures(1, new int[]{capeTex}, 0);
        }

        private void drawCube(int pos, int uv, float x, float y, float z, float w, float h, float d,
                              int atlasX, int atlasY, int atlasW, int atlasH) {
            float x0=x-w/2, x1=x+w/2, y0=y-h/2, y1=y+h/2, z0=z-d/2, z1=z+d/2;
            float u0=atlasX/64f, u1=(atlasX+atlasW)/64f;
            float v0=1f-(atlasY+atlasH)/64f, v1=1f-atlasY/64f;
            float[] verts={
                x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1,
                x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0,
                x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0,
                x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1,
                x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0,
                x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1
            };
            float[] tex={
                u0,v0,u1,v0,u1,v1,u0,v1, u0,v0,u1,v0,u1,v1,u0,v1,
                u0,v0,u1,v0,u1,v1,u0,v1, u0,v0,u1,v0,u1,v1,u0,v1,
                u0,v0,u1,v0,u1,v1,u0,v1, u0,v0,u1,v0,u1,v1,u0,v1
            };
            drawMesh(pos,uv,verts,tex);
        }

        private void drawQuad(int pos,int uv,float[] v,float[] t) {
            float[] verts={v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8],v[9],v[10],v[11]};
            float[] tex={t[0],t[1],t[2],t[3],t[4],t[5],t[6],t[7]};
            drawMesh(pos,uv,verts,tex);
        }

        private void drawMesh(int pos,int uv,float[] verts,float[] tex) {
            vertexBuffer=FloatBuffer.wrap(verts); uvBuffer=FloatBuffer.wrap(tex);
            GLES20.glEnableVertexAttribArray(pos); GLES20.glEnableVertexAttribArray(uv);
            GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,0,vertexBuffer);
            GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,0,uvBuffer);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN,0,4);
            if (verts.length > 12) {
                for(int i=4;i<verts.length/3;i+=4) GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN,i,4);
            }
            GLES20.glDisableVertexAttribArray(pos); GLES20.glDisableVertexAttribArray(uv);
        }

        private int uploadTexture(Bitmap bitmap) {
            if (bitmap == null || bitmap.isRecycled()) return 0;
            int[] ids=new int[1]; GLES20.glGenTextures(1,ids,0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,ids[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);
            return ids[0];
        }

        private int buildProgram() {
            String vs="attribute vec4 aPosition; attribute vec2 aTexCoord; uniform mat4 uMVP; varying vec2 vTexCoord; void main(){gl_Position=uMVP*aPosition;vTexCoord=aTexCoord;}";
            String fs="precision mediump float; varying vec2 vTexCoord; uniform sampler2D uTexture; uniform vec4 uTint; uniform bool uHasTexture; void main(){vec4 c=uTint; if(uHasTexture) c*=texture2D(uTexture,vTexCoord); gl_FragColor=c;}";
            int v=compile(GLES20.GL_VERTEX_SHADER,vs), f=compile(GLES20.GL_FRAGMENT_SHADER,fs);
            int p=GLES20.glCreateProgram(); GLES20.glAttachShader(p,v); GLES20.glAttachShader(p,f); GLES20.glLinkProgram(p);
            return p;
        }

        private int compile(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);return s;}
    }
}
