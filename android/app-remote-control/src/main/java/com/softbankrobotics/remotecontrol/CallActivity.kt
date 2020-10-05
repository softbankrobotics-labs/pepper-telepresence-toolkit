package com.softbankrobotics.remotecontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.aldebaran.qi.sdk.design.activity.conversationstatus.SpeechBarDisplayStrategy
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.softbankrobotics.dx.peppertelepresencecontrol.TelepresenceRobotController
import com.softbankrobotics.helpers.SingleThreadedGlobalScope
import com.softbankrobotics.helpers.TAG
import com.softbankrobotics.helpers.asyncFuture
import com.softbankrobotics.remotevideo.calls.RoomHandler
import com.softbankrobotics.remotevideo.cameras.PepperCameraCapturer
import com.softbankrobotics.remotevideo.exceptions.RemoteVideoException
import com.softbankrobotics.telepresence.BuildConfig
import com.softbankrobotics.telepresence.R
import com.twilio.video.Room
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import kotlinx.android.synthetic.main.activity_call.*
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*
import java.util.Base64.getDecoder

fun String.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    val sb = StringBuilder()
    md.digest(toByteArray()).forEach { sb.append("%02x".format(it)) }
    return sb.toString()
}

fun String.sha512(): String {
    val md = MessageDigest.getInstance("SHA-512")
    val sb = StringBuilder()
    md.digest(toByteArray()).forEach { sb.append("%02x".format(it)) }
    return sb.toString()
}

class CallActivity : RobotActivity(), RobotLifecycleCallbacks, RoomHandler.Listener {

    private val pepperCameraCapturer by lazy {
        PepperCameraCapturer(this).also { it.switchCamera() }
    }
    private val audioManager by lazy { this.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val roomHandler by lazy {
        RoomHandler(
            this, accessTokenGetter, audioManager,
            pepperCameraCapturer, null, null, this
        )
    }
    private var telepresenceRobotController: TelepresenceRobotController? = null
    private var qiContext: QiContext? = null
    private var autoReconnectRoom: String? = null

    val notificationManager by lazy { this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    private var useFirebase: Boolean = false
    private var roomName: String = ""
    private var secret: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.IMMERSIVE)

        setContentView(R.layout.activity_call)
        title_lottie.setMinFrame(61)

        endCallIV.setOnClickListener { finish() }

        useFirebase = intent.getBooleanExtra("useFirebase", false)
        // Use the room name as a secret seed
        val seed = intent.getStringExtra("roomName") ?: ""
        // To get room name used for Twilio, use SHA512(seed)
        roomName = seed.sha512().substring(0, 20)
        // To get secret used to sign Command messages, use MD5(seed).MD5(reversed(seed))
        secret = "${seed.md5()}${seed.reversed().md5()}"

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
        autoReconnectRoom = roomHandler.roomName
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
            .setLargeIcon(BitmapFactory.decodeResource(this.resources, R.mipmap.ic_launcher))
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
        this.qiContext = qiContext
        Log.d(TAG, "About to connect to room")
        pepperCameraCapturer.onRobotFocusGained(qiContext)
        connectToRoom()
        Log.d(TAG, "Done trying to connect to the room, starting control")
        telepresenceRobotController = TelepresenceRobotController(qiContext, true).also {
            it.start()
        }
    }

    override fun onRobotFocusLost() {
        Log.w(TAG, "Robot focus lost")
        pepperCameraCapturer.onRobotFocusLost()
        telepresenceRobotController?.stop()
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
                    Log.i(TAG, "Succeeded in connecting to room.")
                    runOnUiThread {
                        videoStatusTextView.text = getString(R.string.room_connected)
                    }
                } else {
                    if (it.isCancelled) {
                        Log.i(TAG, "Connection to room cancelled.")
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


    override fun onParticipantConnected(identity: String, sid: String) {
        participantIdentity = identity
        videoStatusTextView.text = "Participant $participantIdentity joined"
        title_tv.text = getString(R.string.connected)
        title_lottie.visibility = View.INVISIBLE
        title_image.visibility = View.VISIBLE
    }

    override fun onParticipantDisconnected(identity: String, sid: String) {
        videoStatusTextView.text = "Participant $identity left."

        if (identity != participantIdentity) {
            return
        }
        participantIdentity = null

        title_tv.text = getString(R.string.waiting)
        title_lottie.visibility = View.VISIBLE
        title_image.visibility = View.INVISIBLE
        telepresenceRobotController?.returnToDefaultPose()
    }

    override fun onDisconnected(exception: RemoteVideoException?) {
        videoStatusTextView.text = getString(R.string.room_disconnected)

        participantIdentity = null
        reconnectingProgressBar.visibility = View.GONE;
        if (!disconnectedFromOnDestroy) {
            finish()
        }

        title_tv.text = getString(R.string.offline)
        title_lottie.visibility = View.INVISIBLE
        title_image.visibility = View.VISIBLE
        telepresenceRobotController?.returnToDefaultPose()
    }

    override fun onParticipantVideoTrackSubscribed(
        participantIdentity: String,
        participantSid: String
    ) {
        // Not needed
    }

    override fun onParticipantVideoTrackUnsubscribed(
        participantIdentity: String,
        participantSid: String
    ) {
        // Not needed
    }

    private fun handleCustomJsonCommand(command: TelepresenceRobotController.Command): Boolean {
        // These commands are not handled by the telepresence-control library
        Log.d(TAG, "Evaluating command $command")
        return when (command.name) {
            "use_tablet_camera" -> {
                telepresenceRobotController?.returnToDefaultPose()
                pepperCameraCapturer.switchToBottomCamera()
                true
            }
            "use_head_camera" -> {
                telepresenceRobotController?.returnToDefaultPose()
                pepperCameraCapturer.switchToTopCamera()
                true
            }
            else -> false
        }
    }

    override fun onMessage(trackName: String, message: String) {
        val command = checkMessageValidity(message)
        if (command != null) {
            if (handleCustomJsonCommand(command)) {
                Log.i(TAG, "Executed custom command")
                // Nothing special to do
            } else {
                telepresenceRobotController?.handleJsonCommand(command)?.thenConsume {
                    if (it.hasError()) {
                        Log.w(TAG, "Command finished with error: ${it.errorMessage}")
                        // Uncomment if you want to notify the web client (who will need to handle it)
                        //roomHandler.sendMessage("COMMAND_FAILED")
                    }
                }
            }
        } else {
            Log.w(TAG, "Message command was invalid or empty.")
        }
    }

    private fun checkMessageValidity(message: String): TelepresenceRobotController.Command? {
        return try {
            val jws = Jwts.parserBuilder()
                .setSigningKey(secret.toByteArray())
                .build()
                .parsePlaintextJws(message)
            val payloadBytes = android.util.Base64.decode(jws.body, android.util.Base64.DEFAULT)
            val commandJson = JSONObject(String(payloadBytes))
            val commandName = commandJson.getString("cmd")
            //Parse args to a list of doubles
            val args = ArrayList<Double>()
            commandJson.optJSONArray("args")?.let { jsonArgs ->
                for (i in 0 until jsonArgs.length()) {
                    args.add(jsonArgs.getDouble(i))
                }
            }
            TelepresenceRobotController.Command(commandName, args)
        } catch (ex: JwtException) {
            Log.w(TAG, "Got a JwtException: $ex")
            null
        }
    }

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