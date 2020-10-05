package com.softbankrobotics.remotevideo.cameras

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import com.aldebaran.qi.Function
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.camera.TakePicture
import com.aldebaran.qi.sdk.`object`.image.TimestampedImageHandle
import com.aldebaran.qi.sdk.builder.TakePictureBuilder
import com.softbankrobotics.helpers.TAG
import com.twilio.video.VideoCapturer;
import com.twilio.video.VideoDimensions
import com.twilio.video.VideoFormat
import com.twilio.video.VideoFrame
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import com.twilio.video.VideoPixelFormat
import java.util.concurrent.atomic.AtomicBoolean


class TopCameraCapturer(val qiContext: QiContext): VideoCapturer {
    private lateinit var videoCapturerListener: VideoCapturer.Listener;
    private lateinit var captureFormat: VideoFormat
    var started = AtomicBoolean(false)

    override fun startCapture(captureFormat: VideoFormat, capturerListener: VideoCapturer.Listener) {
        this.videoCapturerListener = capturerListener
        this.captureFormat = captureFormat
        this.started.set(true)
        takePictureWithTopCamera(qiContext) { pic, tmstp ->
            if (this.started.get()) {
                onPicture(pic, tmstp)
            }
        }
    }

    override fun stopCapture() {
        this.started.set(false)
    }

    override fun isScreencast(): Boolean {
        return false
    }

    override fun getSupportedFormats(): MutableList<VideoFormat> {
        val videoFormats = ArrayList<VideoFormat>()
        videoFormats.add(VideoFormat(VideoDimensions(320, 240), 20, VideoPixelFormat.RGBA_8888))
        videoFormats.add(VideoFormat(VideoDimensions(640, 480), 20, VideoPixelFormat.RGBA_8888))
        videoFormats.add(VideoFormat(VideoDimensions(800, 600), 20, VideoPixelFormat.RGBA_8888))
        videoFormats.add(VideoFormat(VideoDimensions(1280, 960), 20, VideoPixelFormat.RGBA_8888))
        return videoFormats
    }

    fun onPicture(picture: Bitmap, timestamp: Long) {
        Log.i(TAG, "Got picture! " + picture.width.toString() + "x" + picture.height.toString())
        val hasSameSize = (captureFormat.dimensions.width == picture.width) && (captureFormat.dimensions.height == picture.height)
        val resized = if (hasSameSize) picture else Bitmap.createScaledBitmap(picture, picture.width, picture.height, false)

        val dimensions = VideoDimensions(resized.width, resized.height);
        val buffer = ByteBuffer.allocate(resized.byteCount)
        resized.copyPixelsToBuffer(buffer)
        val captureTimeNs =
            TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());

        val frame = VideoFrame(buffer.array(), dimensions, VideoFrame.RotationAngle.ROTATION_0, captureTimeNs)

        videoCapturerListener.onFrameCaptured(frame);
        takePictureWithTopCamera(qiContext) { pic, tmstp ->
            if (this.started.get()) {
                onPicture(pic, tmstp)
            }
        }
    }

    private fun takePictureWithTopCamera(qiContext: QiContext, onPictureCb: (Bitmap, Long) -> Unit)
    {
        val takePictureFuture = TakePictureBuilder.with(qiContext).buildAsync()

        val timestampedImageHandleFuture = takePictureFuture.andThenCompose(object:
            Function<TakePicture, Future<TimestampedImageHandle>>
        {
            override fun execute(takePicture: TakePicture): Future<TimestampedImageHandle>
            {
                return takePicture.async().run()
            }
        })

        timestampedImageHandleFuture.andThenConsume { timestampedImageHandle ->
            //Consume take picture action when it's ready
            // get picture
            val encodedImageHandle = timestampedImageHandle.image
            val encodedImage = encodedImageHandle.value

            // get the byte buffer and cast it to byte array
            val buffer = encodedImage.data
            buffer.rewind()
            val pictureBufferSize = buffer.remaining()
            val pictureArray = ByteArray(pictureBufferSize)
            buffer.get(pictureArray)

            val pictureBitmap = BitmapFactory.decodeByteArray(pictureArray, 0, pictureBufferSize)
            onPictureCb(pictureBitmap, timestampedImageHandle.time)
        }
    }

}