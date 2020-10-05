package com.softbankrobotics.remotevideo.cameras

import android.content.Context
import android.util.Log
import com.aldebaran.qi.sdk.QiContext
import com.softbankrobotics.helpers.TAG
import com.twilio.video.*

/*
* Pepper Camera Capturer merge the two pepper cameras into one single VideoCapturer.
*
* */

class PepperCameraCapturer(context: Context) : CameraCapturer(context, CameraSource.FRONT_CAMERA) {

    private lateinit var videoCapturerListener: VideoCapturer.Listener
    private lateinit var videoCaptureFormat: VideoFormat
    internal var useTopCamera = false
    private var captureStarted = false
    enum class VideoSource {HEAD, TABLET}
    private lateinit var source: VideoSource
    private var currentCapturer: VideoCapturer? = null
    private var topCameraCapturer: TopCameraCapturer? = null

    fun getCameraId(): VideoSource {
        return source
    }

    override fun startCapture(captureFormat: VideoFormat, capturerListener: VideoCapturer.Listener) {
        Log.i(TAG, "Start capture with format: " + captureFormat.dimensions.width.toString() + "x" + captureFormat.dimensions.height.toString() + "@" + captureFormat.framerate.toString())
        videoCapturerListener = capturerListener
        videoCaptureFormat = captureFormat
        if (useTopCamera && topCameraCapturer != null) {
            topCameraCapturer?.startCapture(captureFormat, videoCapturerListener)
            source = VideoSource.HEAD
            currentCapturer = topCameraCapturer
        }
        else {
            super.startCapture(captureFormat, videoCapturerListener)
            source = VideoSource.TABLET
            currentCapturer = this
        }
        captureStarted = true
    }

    override fun stopCapture() {
        currentCapturer?.let { if (it == this) super.stopCapture() else it.stopCapture() }
        captureStarted = false
    }

    override fun isScreencast(): Boolean {
        return false
    }

    override fun getSupportedFormats(): MutableList<VideoFormat> {
        /*
        * Pepper Tablet Camera support the following video formats:
        *
        * 176x144@20 with NV21
        * 320x240@20 with NV21
        * 352x288@20 with NV21
        * 480x320@20 with NV21
        * 480x368@20 with NV21
        * 640x480@20 with NV21
        * 720x480@20 with NV21
        * 800x480@20 with NV21
        * 800x600@20 with NV21
        * 1280x720@20 with NV21
        * 1280x960@20 with NV21
        * 1920x1088@20 with NV21
        *
        * Pepper Top Camera support the following video format
        *
        * 1280x960@2 FPS natively
        * and any format lower thanks to resizing
        *
        * Here we try to return a format that fit both cameras: 1280x960 and down...
        * For FPS we choose 20 as it seems to work even if top camera does not support it.
        * */
        val videoFormats = ArrayList<VideoFormat>()
        videoFormats.add(VideoFormat(VideoDimensions(320, 240), 20, VideoPixelFormat.RGBA_8888))
        videoFormats.add(VideoFormat(VideoDimensions(640, 480), 20, VideoPixelFormat.RGBA_8888))
        videoFormats.add(VideoFormat(VideoDimensions(800, 600), 20, VideoPixelFormat.RGBA_8888))
        videoFormats.add(VideoFormat(VideoDimensions(1280, 960), 20, VideoPixelFormat.RGBA_8888))
        return videoFormats
    }

    override fun switchCamera() {
        if (captureStarted) {
            stopCapture()
            useTopCamera = !useTopCamera
            startCapture(videoCaptureFormat, videoCapturerListener)
        } else {
            useTopCamera = !useTopCamera
        }
    }

    fun switchToTopCamera() {
        if (!useTopCamera) {
            switchCamera()
        }
    }

    fun switchToBottomCamera() {
        if (useTopCamera) {
            switchCamera()
        }
    }

    fun onRobotFocusGained(qiContext: QiContext) {
        // When Focus is (re)gained, topCameraCapturer can be used.
        topCameraCapturer = TopCameraCapturer(qiContext)
        if (captureStarted && useTopCamera) {
            stopCapture()
            startCapture(videoCaptureFormat, videoCapturerListener)
        }
    }

    fun onRobotFocusLost() {
        // When Focus is lost, make sure topCameraCapturer is not used. Default to tablet camera
        // if capture is started.
        if (captureStarted) {
            stopCapture()
            topCameraCapturer = null
            startCapture(videoCaptureFormat, videoCapturerListener)
        }
    }

}