package com.lenerd46.spotifyplus.beautifullyrics.entities;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.View;
import android.view.ViewGroup;
import android.view.Choreographer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class AnimatedBackgroundView extends View {
    private static final float DOWNSAMPLE_FACTOR = 0.08f;
    private static final float DESIRED_VIEW_BLUR_RADIUS_PX = 400f;
    private static final int   BOX_BLUR_PASSES = 1;
    private static final long  TRANSITION_DURATION_MS = 2000L;
    private static final int   BLOB_POINTS = 12;

    private final HandlerThread renderThread;
    private final Handler renderHandler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isRendering = new AtomicBoolean(false);
    private final Object swapLock = new Object();

    private Bitmap[] buffers  = new Bitmap[2];
    private Canvas[] canvases = new Canvas[2];
    private int frontIndex = 0;
    private int backIndex = 1;
    private Bitmap renderedBitmap;

    private int viewW;
    private int viewH;
    private int offW = 1;
    private int offH = 1;

    private float sxToOff = 1f;
    private float syToOff = 1f;

    private Bitmap sourceImage;
    private Bitmap shaderBitmap;
    private int baseColor = 0xFF000000;

    private List<Blob> blobs;
    private final List<Path> blobPaths = new ArrayList<>();
    private final List<BitmapShader> blobShaders = new ArrayList<>();
    private final Paint  paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix tmpMatrix = new Matrix();

    private long startTimeMs;
    private volatile boolean isTransitioning = false;
    private Bitmap previousBitmap;
    private long transitionStartMs;

    private volatile boolean blurred = true;
    private int[] blurBufA;
    private int[] blurBufB;
    private int blurBufW;
    private int blurBufH;

    private final ViewGroup root;

    public AnimatedBackgroundView(Context ctx, Bitmap bitmap, ViewGroup root) {
        super(ctx);
        this.root = root;
        setLayerType(LAYER_TYPE_HARDWARE, null);

        this.sourceImage = (bitmap != null) ? bitmap : Bitmap.createBitmap(1,1, Bitmap.Config.ARGB_8888);

        renderThread = new HandlerThread("BGAnim");
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());

        initBlobsAndData();
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        startTimeMs = SystemClock.elapsedRealtime();
    }

    public void updateImage(Bitmap newImage) {
        if (newImage == null || newImage.isRecycled()) return;

        synchronized (swapLock) {
            if (renderedBitmap != null) {
                previousBitmap = renderedBitmap.copy(Bitmap.Config.ARGB_8888, false);
                transitionStartMs = SystemClock.elapsedRealtime();
                isTransitioning = true;
            }
        }

        sourceImage = newImage;

        regenerateBlobsForCoverage();
        generateAdaptiveBlobs();
        prepareBlobResources();
        postInvalidateOnAnimation();
    }

    public void setBlurred(boolean enable) {
        if (blurred == enable) return;
        blurred = enable;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        allocateBuffersIfNeeded(getWidth(), getHeight());
        rebuildShaderBitmapIfNeeded();
        regenerateBlobsForCoverage();
        prepareBlobResources();
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        Choreographer.getInstance().removeFrameCallback(frameCallback);
        renderHandler.removeCallbacksAndMessages(null);
        renderThread.quitSafely();

        synchronized (swapLock) {
            if (previousBitmap != null) { previousBitmap.recycle(); previousBitmap = null; }
            if (shaderBitmap != null) { shaderBitmap.recycle(); shaderBitmap = null; }
            if (buffers[0] != null) { buffers[0].recycle(); buffers[0] = null; }
            if (buffers[1] != null) { buffers[1].recycle(); buffers[1] = null; }

            renderedBitmap = null;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        allocateBuffersIfNeeded(w, h);
        rebuildShaderBitmapIfNeeded();
        regenerateBlobsForCoverage();
        prepareBlobResources();
    }

    private final Runnable renderRunnable = () -> {
        if (!isRendering.compareAndSet(false, true)) return;

        try {
            if (offW <= 0 || offH <= 0 || buffers[backIndex] == null) return;

            if (shaderBitmap == null || shaderBitmap.isRecycled() || shaderBitmap.getWidth() != offW || shaderBitmap.getHeight() != offH) {
                rebuildShaderBitmapIfNeeded();
                prepareBlobResources();

                if (shaderBitmap == null) return;
            }

            final long now = SystemClock.elapsedRealtime();
            final float t  = (now - startTimeMs) / 10000f * 1.5f;

            final Canvas c = canvases[backIndex];
            c.drawColor(baseColor);

            for (int i = 0; i < blobs.size(); i++) {
                Path path = blobPaths.get(i);
                Blob b = blobs.get(i);
                updateBlobPath(path, b, t, i);
                updateShaderMatrix(tmpMatrix, blobShaders.get(i), b, t, i);
                paint.setShader(blobShaders.get(i));
                c.drawPath(path, paint);
            }

            paint.setShader(null);

            if (blurred) {
                fastBoxBlurOpaque(buffers[backIndex], effectiveOffscreenRadius(DESIRED_VIEW_BLUR_RADIUS_PX), BOX_BLUR_PASSES);
            }

            synchronized (swapLock) {
                frontIndex = backIndex;
                renderedBitmap = buffers[frontIndex];
                backIndex = 1 - frontIndex;
            }

            mainHandler.post(this::postInvalidateOnAnimation);

        } catch (Throwable ignore) {
        } finally {
            isRendering.set(false);
        }
    };

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        synchronized (swapLock) {
            final Bitmap current = renderedBitmap;
            final Bitmap prev    = previousBitmap;
            final long   tStart  = transitionStartMs;

            if (current == null) {
                canvas.drawColor(baseColor);
                return;
            }

            Rect dst = new Rect(0, 0, getWidth(), getHeight());

            if (isTransitioning && prev != null) {
                float p = Math.min((SystemClock.elapsedRealtime() - tStart) / (float) TRANSITION_DURATION_MS, 1f);

                paint.setAlpha((int)((1f - p) * 255)); canvas.drawBitmap(prev, null, dst, paint);
                paint.setAlpha((int)(p * 255));        canvas.drawBitmap(current, null, dst, paint);
                paint.setAlpha(255);

                if (p >= 1f) {
                    isTransitioning = false;
                    if (previousBitmap != null) { previousBitmap.recycle(); previousBitmap = null; }
                }
            } else {
                canvas.drawBitmap(current, null, dst, null);
            }
        }
    }

    private float toSrcX(float offX) { return offX / Math.max(1e-6f, sxToOff); }
    private float toSrcY(float offY) { return offY / Math.max(1e-6f, syToOff); }

    private float toSrcR_fromEffOff(float effOffscreenR) {
        float s = Math.min(sxToOff, syToOff);
        return (effOffscreenR / 3f) / Math.max(1e-6f, s);
    }

    private void addBlobOff(List<Blob> out, float offX, float offY, float effOffR, float scale, float rot, boolean opp) {
        out.add(new Blob(toSrcX(offX), toSrcY(offY), toSrcR_fromEffOff(effOffR), scale, rot, opp));
    }

    private void regenerateBlobsForCoverage() {
        if (offW <= 0 || offH <= 0) return;

        final float W = offW, H = offH, M = Math.max(W, H), m = Math.min(W, H);
        final float diag = (float)Math.hypot(W, H);

        final float R_CENTER = 0.60f * M;
        final float R_CORNER = 0.55f * M;
        final float R_EDGE = 0.48f * M;
        final float R_RING = 0.28f * M;

        ArrayList<Blob> out = new ArrayList<>(12);
        addBlobOff(out, W*0.5f, H*0.5f, R_CENTER, 1.35f,   0f, false);

        float o = 0.10f;
        addBlobOff(out, -W*o, -H*o, R_CORNER, 1.15f, 15f, true );
        addBlobOff(out, W*(1+o), -H*o, R_CORNER, 1.15f, -15f, false);
        addBlobOff(out, -W*o, H*(1+o), R_CORNER, 1.15f, -10f, true );
        addBlobOff(out, W*(1+o), H*(1+o), R_CORNER, 1.15f, 10f, false);

        addBlobOff(out, W*0.50f, -H*0.12f, R_EDGE, 1.10f, 30f, true );
        addBlobOff(out, W*0.50f, H*1.12f, R_EDGE, 1.10f, -30f, false);
        addBlobOff(out, -W*0.12f, H*0.50f, R_EDGE, 1.10f, -40f, true );
        addBlobOff(out, W*1.12f, H*0.50f, R_EDGE, 1.10f, 40f, false);

        int ringCount = 4;
        float ringR = 0.38f * m;
        for (int i = 0; i < ringCount; i++) {
            float ang = (float)(i * (Math.PI * 2 / ringCount) + Math.PI / 4);
            float cx = W * 0.5f + ringR*(float)Math.cos(ang) * (W >= H ? 1.10f : 0.90f);
            float cy = H * 0.5f + ringR*(float)Math.sin(ang) * (H > W ? 1.10f : 0.90f);
            float jx = ((i & 1) == 0 ? 0.04f : -0.04f) * W;
            float jy = ((i & 1) == 1 ? 0.04f : -0.04f) * H;
            addBlobOff(out, cx + jx, cy + jy, R_RING, 1.12f, (i % 2 == 0 ? 25f : -25f), (i % 2 == 0));
        }

        while (out.size() > 12) out.remove(out.size()-1);
        this.blobs = out;
    }

    private Choreographer.FrameCallback frameCallback;

    private void initBlobsAndData() {
        frameCallback = frameTimeNanos -> {
            if (getWindowToken() == null) return;

            renderHandler.post(renderRunnable);
            Choreographer.getInstance().postFrameCallback(frameCallback);
        };
    }

    private void allocateBuffersIfNeeded(int vw, int vh) {
        if (vw <= 0 || vh <= 0) return;
        viewW = vw; viewH = vh;

        int targetW = Math.max(1, Math.round(vw * DOWNSAMPLE_FACTOR));
        int targetH = Math.max(1, Math.round(vh * DOWNSAMPLE_FACTOR));
        if (buffers[0] != null && buffers[0].getWidth() == targetW && buffers[0].getHeight() == targetH) return;

        synchronized (swapLock) {
            if (buffers[0] != null) { buffers[0].recycle(); buffers[0] = null; }
            if (buffers[1] != null) { buffers[1].recycle(); buffers[1] = null; }
            renderedBitmap = null;
        }

        buffers[0] = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
        buffers[1] = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
        canvases[0] = new Canvas(buffers[0]);
        canvases[1] = new Canvas(buffers[1]);

        offW = targetW; offH = targetH;
        sxToOff = offW / (float) Math.max(1, sourceImage.getWidth());
        syToOff = offH / (float) Math.max(1, sourceImage.getHeight());

        ensureBlurArrays();
        synchronized (swapLock) {
            frontIndex = 0; backIndex = 1;
            renderedBitmap = buffers[frontIndex];
        }
    }

    private void prepareBlobResources() {
        blobPaths.clear();
        blobShaders.clear();
        if (shaderBitmap == null || shaderBitmap.isRecycled()) return;

        for (int i = 0; i < blobs.size(); i++) {
            blobPaths.add(new Path());
            blobShaders.add(new BitmapShader(shaderBitmap, Shader.TileMode.MIRROR, Shader.TileMode.REPEAT));
        }
    }

    private void rebuildShaderBitmapIfNeeded() {
        if (offW <= 0 || offH <= 0 || sourceImage == null || sourceImage.isRecycled()) return;

        Bitmap base = Bitmap.createBitmap(offW, offH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(base);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        Rect src = centerCropRect(sourceImage.getWidth(), sourceImage.getHeight(), offW, offH);
        Rect dst = new Rect(0, 0, offW, offH);

        ColorMatrix m = new ColorMatrix(); m.setSaturation(2f);
        p.setColorFilter(new ColorMatrixColorFilter(m));
        c.drawBitmap(sourceImage, src, dst, p);
        p.setColorFilter(null);

        baseColor = computeAverageColor(base);

        synchronized (swapLock) {
            if (shaderBitmap != null && !shaderBitmap.isRecycled()) shaderBitmap.recycle();
            shaderBitmap = base;
        }

        generateAdaptiveBlobs();
        prepareBlobResources();
    }

    private void generateAdaptiveBlobs() {
        if (sourceImage == null) return;

        final float w = Math.max(1, sourceImage.getWidth());
        final float h = Math.max(1, sourceImage.getHeight());
        final float min = Math.min(w, h);
        final float max = Math.max(w, h);
        final float aspect = max / min;

        final float baseR = min * 0.06f;
        final float bigR  = baseR * 2.2f;
        final float medR  = baseR * 1.4f;

        long seed = 73856093L * (long) w ^ 19349663L * (long) h ^ baseColor;
        Random rnd = new Random(seed);

        float r = ((baseColor >> 16) & 0xFF) / 255f;
        float g = ((baseColor >> 8)  & 0xFF) / 255f;
        float b = ( baseColor        & 0xFF) / 255f;
        float gray = 0.299f * r + 0.587f * g + 0.114f * b;
        float chroma = (Math.abs(r - gray) + Math.abs(g - gray) + Math.abs(b - gray)) / 3f;

        int ring1Count = 4;
        int ring2Count = chroma > 0.12f ? 5 : 4;

        float ring1 = 0.28f;
        float ring2 = aspect > 1.15f ? 0.58f : 0.50f;
        float stretchX = (w >= h) ? aspect * 0.85f : 1f;
        float stretchY = (h >  w) ? aspect * 0.85f : 1f;

        List<Blob> out = new ArrayList<>(12);

        out.add(new Blob(w * 0.20f, h * 0.20f, bigR, 1.10f + rnd.nextFloat() * 0.15f, 10f, false));
        out.add(new Blob(w * 0.80f, h * 0.80f, bigR, 1.10f + rnd.nextFloat() * 0.15f, -10f, true ));
        out.add(new Blob(w * 0.50f, h * 0.50f, medR, 1.45f + rnd.nextFloat()*0.25f,  0f,  false));

        out.add(new Blob(w * 0.50f, h * 0.12f, medR, 1.15f + rnd.nextFloat() * 0.20f, 25f, true ));
        out.add(new Blob(w * 0.12f, h * 0.50f, medR, 1.10f + rnd.nextFloat() * 0.20f, -35f, false));
        out.add(new Blob(w * 0.88f, h * 0.50f, medR, 1.10f + rnd.nextFloat() * 0.20f, 35f, true ));
        out.add(new Blob(w * 0.50f, h * 0.88f, medR, 1.15f + rnd.nextFloat() * 0.20f, -25f, false));

        for (int i = 0; i < ring1Count; i++) {
            float ang = (float) (Math.toRadians(90 * i + 45));
            float cx = 0.5f + ring1 * (float)Math.cos(ang) * stretchX;
            float cy = 0.5f + ring1 * (float)Math.sin(ang) * stretchY;
            float jx = (rnd.nextFloat() - 0.5f) * 0.06f;
            float jy = (rnd.nextFloat() - 0.5f) * 0.06f;
            float rad = baseR * (1.1f + rnd.nextFloat() * 0.4f);
            float sc  = 1.20f + rnd.nextFloat() * 0.30f;
            float rot = rnd.nextFloat() * 60f - 30f;

            out.add(new Blob(clamp01(cx + jx) * w, clamp01(cy + jy) * h, rad, sc, rot, (i % 2 == 0)));
        }

        for (int i = 0; i < ring2Count; i++) {
            float ang = (float) (Math.toRadians((360f / ring2Count) * i));
            float cx = 0.5f + ring2 * (float)Math.cos(ang) * stretchX;
            float cy = 0.5f + ring2 * (float)Math.sin(ang) * stretchY;
            float jx = (rnd.nextFloat() - 0.5f) * 0.08f;
            float jy = (rnd.nextFloat() - 0.5f) * 0.08f;
            float rad = baseR * (1.0f + rnd.nextFloat()*0.5f);
            float sc  = 1.05f + rnd.nextFloat()*0.35f;
            float rot = rnd.nextFloat()*120f - 60f;

            out.add(new Blob(clamp01(cx + jx) * w, clamp01(cy + jy) * h, rad, sc, rot, (i % 2 != 0)));
        }

        while (out.size() > 12) out.remove(out.size() - 1);
        this.blobs = out;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static Rect centerCropRect(int sw, int sh, int dw, int dh) {
        float sa = sw / (float) sh, da = dw / (float) dh;

        if (sa > da) {
            int nw = Math.round(sh * da), x = (sw - nw) / 2;
            return new Rect(x, 0, x + nw, sh);
        } else {
            int nh = Math.round(sw / da), y = (sh - nh) / 2;
            return new Rect(0, y, sw, y + nh);
        }
    }

    private static int computeAverageColor(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int stepX = Math.max(1, w / 16), stepY = Math.max(1, h / 16);
        long rs=0, gs=0, bs=0, n=0;
        for (int y=0; y<h; y+=stepY) {
            for (int x=0; x<w; x+=stepX) {
                int c = b.getPixel(x, y);
                rs += (c >> 16) & 0xFF;
                gs += (c >> 8) & 0xFF;
                bs += c & 0xFF;
                n++;
            }
        }
        int r = (int)(rs / Math.max(1,n));
        int g = (int)(gs / Math.max(1,n));
        int bl= (int)(bs / Math.max(1,n));

        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private void updateBlobPath(Path path, Blob blob, float time, int index) {
        final float step = 360f / BLOB_POINTS;
        float cx = blob.centerX * sxToOff;
        float cy = blob.centerY * syToOff;
        float r  = blob.radius * Math.min(sxToOff, syToOff);

        path.rewind();
        for (int i = 0; i < BLOB_POINTS; i++) {
            float a = (float)Math.toRadians(i * step);
            float wob = (float)(Math.sin(a * 3 + time * 2 + index) * r * 0.05f);
            float x = cx + (r + wob) * (float)Math.cos(a) * 3f;
            float y = cy + (r + wob) * (float)Math.sin(a) * 3f;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }

        path.close();
    }

    private void updateShaderMatrix(Matrix m, BitmapShader shader, Blob b, float time, int idx) {
        float sx = b.scale + 0.2f * (float)Math.sin(time + idx);
        float sy = b.scale + 0.12f * (float)Math.cos(time + idx);
        float rot = (b.rotation + time * 0.3f * idx) * (b.opposite ? -1f : 1f);
        float skewX = 0.6f * (float)Math.sin(time * (0.5f + idx * 0.1f));
        float skewY = 0.4f * (float)Math.cos(time * (0.3f + idx * 0.1f));

        float tx = b.centerX * sxToOff;
        float ty = b.centerY * syToOff;

        m.reset();
        m.postRotate(rot);
        m.postScale(sx, sy);
        m.postTranslate(tx, ty);
        m.postSkew(skewX, skewY);
        shader.setLocalMatrix(m);
    }

    private int effectiveOffscreenRadius(float desiredViewPx) {
        if (viewW <= 0) return 8;
        float scale = offW / (float) viewW;
        int r = Math.max(1, Math.round(desiredViewPx * scale));

        return Math.min(r, Math.max(offW, offH) / 2);
    }

    private void ensureBlurArrays() {
        if (blurBufA != null && blurBufW == offW && blurBufH == offH) return;

        blurBufW = offW; blurBufH = offH;
        blurBufA = new int[offW * offH];
        blurBufB = new int[offW * offH];
    }

    private void fastBoxBlurOpaque(Bitmap srcDst, int radius, int passes) {
        if (radius <= 0 || passes <= 0) return;
        ensureBlurArrays();

        srcDst.getPixels(blurBufA, 0, offW, 0, 0, offW, offH);
        for (int i = 0; i < passes; i++) {
            boxBlurHorizontalOpaque(blurBufA, blurBufB, offW, offH, radius);
            boxBlurVerticalOpaque(blurBufB, blurBufA, offW, offH, radius);
        }

        for (int i = 0; i < blurBufA.length; i++) blurBufA[i] |= 0xFF000000;
        srcDst.setPixels(blurBufA, 0, offW, 0, 0, offW, offH);
    }

    private static void boxBlurHorizontalOpaque(int[] src, int[] dst, int w, int h, int r) {
        final int div = r * 2 + 1;
        for (int y = 0; y < h; y++) {
            int tr= 0;
            int tg = 0;
            int tb = 0;
            int yi = y * w;

            for (int x = -r; x <= r; x++) {
                int px = clamp(x, 0, w-1);
                int c = src[yi + px];

                tr += (c >> 16) & 0xFF;
                tg += (c >> 8) & 0xFF;
                tb += c & 0xFF;
            }

            for (int x = 0; x < w; x++) {
                dst[yi + x] = 0xFF000000 | ((tr / div) << 16) | ((tg / div) << 8) | (tb / div);

                int xOut = clamp(x - r, 0, w - 1);
                int xIn  = clamp(x + r + 1, 0, w - 1);

                int cOut = src[yi + xOut];
                int cIn  = src[yi + xIn];

                tr += (((cIn >> 16) & 0xFF) - ((cOut >> 16) & 0xFF));
                tg += (((cIn >> 8) & 0xFF) - ((cOut >> 8) & 0xFF));
                tb += (((cIn) & 0xFF) - ((cOut) & 0xFF));
            }
        }
    }

    private static void boxBlurVerticalOpaque(int[] src, int[] dst, int w, int h, int r) {
        final int div = r * 2 + 1;
        for (int x = 0; x < w; x++) {
            int tr = 0;
            int tg = 0;
            int tb = 0;

            for (int y = -r; y <= r; y++) {
                int py = clamp(y, 0, h - 1);
                int c = src[py * w + x];

                tr += (c >> 16) & 0xFF;
                tg += (c >> 8) & 0xFF;
                tb += c & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                dst[y * w + x] = 0xFF000000 | ((tr / div) << 16) | ((tg / div) << 8) | (tb / div);

                int yOut = clamp(y - r, 0, h - 1);
                int yIn  = clamp(y + r + 1, 0, h - 1);

                int cOut = src[yOut * w + x];
                int cIn  = src[yIn  * w + x];

                tr += (((cIn >> 16) & 0xFF) - ((cOut >> 16) & 0xFF));
                tg += (((cIn >> 8) & 0xFF) - ((cOut >> 8)  & 0xFF));
                tb += (((cIn) & 0xFF) - ((cOut) & 0xFF));
            }
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return (v < lo) ? lo : (v > hi ? hi : v);
    }

    public static class Blob {
        public final float centerX;
        public final float centerY;
        public final float radius;
        public final float scale;
        public final float rotation;
        public final boolean opposite;

        public Blob(float cx, float cy, float r, float s, float rot, boolean opp) {
            centerX = cx; centerY = cy; radius = r; scale = s; rotation = rot; opposite = opp;
        }
    }
}
