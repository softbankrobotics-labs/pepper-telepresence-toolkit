package com.softbankrobotics.telepresence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.aldebaran.qi.sdk.design.activity.conversationstatus.SpeechBarDisplayStrategy
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.softbankrobotics.helpers.SingleThreadedGlobalScope
import com.softbankrobotics.helpers.TAG
import com.softbankrobotics.helpers.asyncFuture
import com.softbankrobotics.remotevideo.calls.RoomHandler
import com.softbankrobotics.remotevideo.cameras.PepperCameraCapturer
import com.softbankrobotics.remotevideo.exceptions.RemoteVideoException
import com.twilio.video.Room
import kotlinx.android.synthetic.main.activity_call.*
import kotlinx.coroutines.tasks.await
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*

fun String.sha512(): String {
    val md = MessageDigest.getInstance("SHA-512")
    val sb = StringBuilder()
    md.digest(toByteArray()).forEach { sb.append("%02x".format(it)) }
    return sb.toString()
}

class CallActivity : RobotActivity(), RobotLifecycleCallbacks, RoomHandler.Listener {

    private val pepperCameraCapturer by lazy { PepperCameraCapturer(this) }
    private val audioManager by lazy { this.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val roomHandler by lazy {
        RoomHandler(
            this, accessTokenGetter, audioManager,
            pepperCameraCapturer, primaryVideoView, thumbnailVideoView, this
        )
    }
    private var qiContext: QiContext? = null
    private var autoReconnectRoom: String? = null

    val notificationManager by lazy { this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    private var useFirebase: Boolean = false
    private var roomName: String = ""

    /*********************
     * Android Lifecycle *
     *********************/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.IMMERSIVE)

        setContentView(R.layout.activity_call)

        endCallIV.setOnClickListener { finish() }

        useFirebase = intent.getBooleanExtra("useFirebase", false)
        // Use the room name as a secret seed
        val seed = intent.getStringExtra("roomName") ?: ""
        // To get room name used for Twilio, use SHA512(seed)
        roomName = seed.sha512().substring(0, 20)

        QiSDK.register(this, this)

        audioManager.isSpeakerphoneOn = true

        createNotification()
    }

    override fun onStart() {
        super.onStart()

        autoReconnectRoom?.let { roomName ->
            roomHandler.connectToRoom(roomName)
            autoReconnectRoom = null
        }
    }

    override fun onResume() {
        super.onResume()

        hideSystemBars()

        roomHandler.resume()
        audioManager.isSpeakerphoneOn = true
        if (roomHandler.isConnecting())
            reconnectingProgressBar.visibility = View.VISIBLE
        else
            reconnectingProgressBar.visibility = View.GONE

        if (roomHandler.state == Room.State.CONNECTED)
            videoStatusTextView.text = getString(R.string.room_connected)
    }

    override fun onPause() {
        super.onPause()
        roomHandler.pauseVideo()
    }

    override fun onStop() {
        super.onStop()
        autoReconnectRoom = roomHandler.roomName // Will be null if we're already disconnected
        disconnectedFromOnDestroy = true
        pepperCameraCapturer.stopCapture()
        roomHandler.disconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationManager.cancelAll()
        QiSDK.unregister(this, this)
    }

    private fun hideSystemBars() {
        window.decorView.apply {
            systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }

    /*********************
     *    GUI binding    *
     *********************/

    private fun createNotification() {

        val CHANNEL_ID = "Recording"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_video_call_white_24dp)
            .setLargeIcon(BitmapFactory.decodeResource(this.resources, R.mipmap.teleoperation))
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.room_recording))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = "Recording" // The user-visible name of the channel.
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
            notificationManager.createNotificationChannel(mChannel)
        }

        with(NotificationManagerCompat.from(this)) {
            notify(0, builder.build())
        }
    }

    /**********************
     * Robot Lifecycle
     **********************/

    override fun onRobotFocusGained(qiContext: QiContext) {
        Log.i(TAG, "Robot focus gained")
        pepperCameraCapturer.onRobotFocusGained(qiContext)
        this.qiContext = qiContext

        connectToRoom()
    }


    override fun onRobotFocusLost() {
        Log.w(TAG, "Robot focus lost")
        pepperCameraCapturer.onRobotFocusLost()
        qiContext = null
    }

    override fun onRobotFocusRefused(reason: String?) {
        Log.e(TAG, "Robot focus refused because $reason")
    }

    /**********************
     * Twilio Functions
     **********************/

    private var participantIdentity: String? = null
    private var disconnectedFromOnDestroy = false

    private fun connectToRoom() {
        if (roomName.isNotEmpty()) {
            roomHandler.connectToRoom(roomName).thenConsume {
                if (it.isSuccess && it.value.isSuccess) {
                    Log.i(TAG, "Succeeded in connecting to room")
                    runOnUiThread {
                        primaryVideoView.visibility = View.VISIBLE
                        videoStatusTextView.text = getString(R.string.room_connected)
                    }
                } else {
                    if (it.isCancelled) {
                        Log.w(TAG, "Connection to room cancelled.")
                    } else {
                        val error = if (it.hasError()) it.error else it.value.exceptionOrNull()
                        Log.w(TAG, "Connection to room failed: $error.")
                        runOnUiThread {
                            videoStatusTextView.text = "Failed to connect: ${error}"
                        }
                    }
                }
            }
        }
    }

    private val accessTokenGetter = object : RoomHandler.AccessTokenGetter {
        private val TWILIO_ACCESS_TOKEN = BuildConfig.TWILIO_ACCESS_TOKEN

        override fun getAccessToken(): Future<String> {
            if (!useFirebase)
                return Future.of(TWILIO_ACCESS_TOKEN)
            else
                return retrieveAccessTokenFromFirebase()
        }
    }

    override fun onReconnecting(exception: RemoteVideoException) {
        videoStatusTextView.text = getString(R.string.room_reconnecting)
        reconnectingProgressBar.visibility = View.VISIBLE;
    }

    override fun onReconnected() {
        videoStatusTextView.text = getString(R.string.room_connected)
        reconnectingProgressBar.visibility = View.GONE
    }

    private fun robotSay(phrase: String) {
        qiContext?.let { qiContext ->
            SayBuilder.with(qiContext).withText(phrase).buildAsync()
                .andThenConsume { it.async().run() }
        }
    }

    override fun onParticipantConnected(identity: String, sid: String) {
        if (thumbnailVideoView.visibility == View.VISIBLE) {
            Toast.makeText(
                this, "Multiple participants are not currently support in this UI",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        thumbnailVideoView.visibility = View.VISIBLE
        primaryVideoView.mirror = false
        participantIdentity = identity
        videoStatusTextView.text = "Participant $participantIdentity joined"
        robotSay("$identity is connected")
    }

    override fun onParticipantDisconnected(identity: String, sid: String) {
        videoStatusTextView.text = "Participant $identity left."

        if (identity != participantIdentity) {
            return
        }
        thumbnailVideoView.visibility = View.GONE
        primaryVideoView.mirror = true

        robotSay("$identity is disconnected")
    }

    override fun onDisconnected(exception: RemoteVideoException?) {
        videoStatusTextView.text = getString(R.string.room_disconnected)
        reconnectingProgressBar.visibility = View.GONE;
        if (!disconnectedFromOnDestroy) {
            finish()
        }
    }

    override fun onParticipantVideoTrackSubscribed(
        participantIdentity: String,
        participantSid: String
    ) {
        thumbnailVideoView.visibility = View.VISIBLE
        primaryVideoView.mirror = false
    }

    override fun onParticipantVideoTrackUnsubscribed(
        participantIdentity: String,
        participantSid: String
    ) {
        thumbnailVideoView.visibility = View.GONE
        primaryVideoView.mirror = true
    }

    override fun onMessage(trackName: String, message: String) {}

    /************
     * Firebase
     ************/

    private fun retrieveAccessTokenFromFirebase() = SingleThreadedGlobalScope.asyncFuture {
        try {
            val data = hashMapOf("identity" to UUID.randomUUID().toString())
            val result =
                Firebase.functions.getHttpsCallable("token").call(data).await().data as Map<*, *>
            result.get("token") as String
        } catch (e: Exception) {
            throw RuntimeException("Error retrieving firebase token: $e")
        }
    }
}