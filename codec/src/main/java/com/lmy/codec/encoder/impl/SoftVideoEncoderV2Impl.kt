package com.lmy.codec.encoder.impl

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaFormat
import android.opengl.EGLContext
import android.opengl.GLES20
import android.os.Handler
import com.lmy.codec.encoder.Encoder
import com.lmy.codec.entity.CodecContext
import com.lmy.codec.entity.PresentationTimer
import com.lmy.codec.helper.CodecHelper
import com.lmy.codec.helper.Libyuv
import com.lmy.codec.util.debug_e
import com.lmy.codec.wrapper.CodecTextureWrapper
import com.lmy.codec.x264.CacheX264Encoder
import com.lmy.codec.x264.SurfaceX264Encoder
import com.lmy.codec.x264.X264Encoder
import java.nio.ByteBuffer

class SoftVideoEncoderV2Impl(var context: CodecContext,
                             private val textureId: IntArray,
                             private var eglContext: EGLContext,
                             var codecWrapper: CodecTextureWrapper? = null,
                             var codec: SurfaceX264Encoder? = null,
                             private var pTimer: PresentationTimer = PresentationTimer(context.video.fps),
                             override var onPreparedListener: Encoder.OnPreparedListener? = null,
                             override var onRecordListener: Encoder.OnRecordListener? = null)
    : Encoder, CacheX264Encoder.OnSampleListener {

    override fun onFormatChanged(format: MediaFormat) {
        onSampleListener?.onFormatChanged(this, format)
    }

    override fun onSample(info: MediaCodec.BufferInfo, data: ByteBuffer) {
        pTimer.record()
        info.presentationTimeUs = pTimer.presentationTimeUs
        onSampleListener?.onSample(this, info, data)
        onRecordListener?.onRecord(this, info.presentationTimeUs)
    }

    private lateinit var format: MediaFormat
    private val mEncodingSyn = Any()
    private var mEncoding = false
    private var inited = false

    private var onSampleListener: Encoder.OnSampleListener? = null
    override fun setOnSampleListener(listener: Encoder.OnSampleListener) {
        onSampleListener = listener
    }

    init {
        initCodec()
        codecWrapper = CodecTextureWrapper(codec!!.surface, textureId, eglContext)
        pTimer.reset()
        inited = true
    }

    private fun initCodec() {
        format = CodecHelper.createVideoFormat(context, true)!!
        codec = SurfaceX264Encoder(CacheX264Encoder(X264Encoder(format, Libyuv.COLOR_I420)),
                this)
        codec!!.setProfile("high")
        codec!!.setLevel(31)
        codec?.onSampleListener = this
        Handler().postDelayed({
            debug_e("onPreparedListener ${null == onPreparedListener}")
            onPreparedListener?.onPrepared(this)
        }, 1000)
    }

    override fun start() {
        synchronized(mEncodingSyn) {
            pTimer.start()
            mEncoding = true
        }
    }

    override fun pause() {
        synchronized(mEncodingSyn) {
            mEncoding = false
        }
    }

    override fun stop() {
        pause()
        debug_e("Video encoder stopping")
        codec?.release()
        codecWrapper?.release()
        codecWrapper = null
        debug_e("Video encoder stop")
    }


    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        synchronized(mEncodingSyn) {
            if (mEncoding && inited) {
                codecWrapper?.egl?.makeCurrent()
                GLES20.glViewport(0, 0, context.video.width, context.video.height)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                GLES20.glClearColor(0.3f, 0.3f, 0.3f, 0f)
                codecWrapper?.drawTexture(null)
                codecWrapper?.egl?.swapBuffers()
            }
        }
    }
}