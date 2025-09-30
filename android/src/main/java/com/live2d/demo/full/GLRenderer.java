/*
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at http://live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.live2d.demo.full;

import android.opengl.GLSurfaceView;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLRenderer implements GLSurfaceView.Renderer {
    // Called at initialization (when the drawing context is lost and recreated).
    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        try {
            LAppDelegate.getInstance().onSurfaceCreated();
        } catch (Exception e) {
            android.util.Log.e("GLRenderer", "Error in onSurfaceCreated: " + e.getMessage(), e);
        }
    }

    // Mainly called when switching between landscape and portrait.
    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        try {
            LAppDelegate delegate = LAppDelegate.getInstance();
            if (delegate.getView() != null) {
                delegate.onSurfaceChanged(width, height);
            } else {
                android.util.Log.w("GLRenderer", "LAppDelegate view is null, skipping onSurfaceChanged");
            }
        } catch (Exception e) {
            android.util.Log.e("GLRenderer", "Error in onSurfaceChanged: " + e.getMessage(), e);
        }
    }

    // Called repeatedly for drawing.
    @Override
    public void onDrawFrame(GL10 unused) {
        try {
            LAppDelegate delegate = LAppDelegate.getInstance();
            if (delegate.getView() != null) {
                delegate.run();
            }
        } catch (Exception e) {
            android.util.Log.e("GLRenderer", "Error in onDrawFrame: " + e.getMessage(), e);
        }
    }
}
