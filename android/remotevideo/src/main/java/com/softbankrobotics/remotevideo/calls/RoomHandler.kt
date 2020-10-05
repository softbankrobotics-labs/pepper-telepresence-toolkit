package com.softbankrobotics.remotevideo.calls

import com.softbankrobotics.helpers.TAG
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.Promise
import com.softbankrobotics.helpers.SingleThreadedGlobalScope
import com.softbankrobotics.helpers.asyncFuture
import com.softbankrobotics.helpers.await
import com.softbankrobotics.remotevideo.exceptions.RemoteVideoException
import com.twilio.video.CameraCapturer
import com.twilio.video.ConnectOptions
import com.twilio.video.EncodingParameters
import com.twilio.video.LocalAudioTrack
import com.twilio.video.LocalDataTrack
import com.twilio.video.LocalParticipant
import com.twilio.video.LocalVideoTrack
import com.twilio.video.LogLevel
import com.twilio.video.OpusCodec
import com.twilio.video.RemoteDataTrack
import com.twilio.video.RemoteDataTrackPublication
import com.twilio.video.RemoteParticipant
import com.twilio.video.RemoteVideoTrack
import com.twilio.video.RemoteVideoTrackPublication
import com.twilio.video.Room
import com.twilio.video.TwilioException
import com.twilio.video.Video
import com.twilio.video.VideoRenderer
import com.twilio.video.VideoView
import com.twilio.video.Vp8Codec
import kotlinx.coroutines.channels.Channel
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.util.Collections

class RoomHandler(val context: Context,
                  val accessTokenGetter: AccessTokenGetter,
                  val audioManager: AudioManager,
                  val cameraCapturer: CameraCapturer,
                  val mainRemoteVideoView: VideoView?,
                  val localVideoView: VideoView? = null,
                  val listener: Listener? = null)
    : Room.Listener, LoggingParticipantListener(), RemoteDataTrack.Listener {

    interface AccessTokenGetter {
        fun getAccessToken(): Future<String>
    }

    interface Listener {
        //fun onConnected()
        //fun onConnectFailure(twilioException: TwilioException)
        fun onReconnecting(exception: RemoteVideoException)
        fun onReconnected()
        fun onDisconnected(exception: RemoteVideoException?)
        fun onParticipantConnected(participantIdentity: String, participantSid: String)
        fun onParticipantDisconnected(participantIdentity: String, participantSid: String)
        fun onParticipantVideoTrackSubscribed(participantIdentity: String, participantSid: String)
        fun onParticipantVideoTrackUnsubscribed(participantIdentity: String, participantSid: String)
        fun onMessage(trackName: String, message: String)
    }

    private val audioCodec = OpusCodec()
    private val videoCodec = Vp8Codec()
    private val encodingParameters = EncodingParameters(0, 0)
    private val enableAutomaticSubscription = true

    private var localDataTrack: LocalDataTrack? = null
    private var localAudioTrack: LocalAudioTrack? = null
    private var localVideoTrack: LocalVideoTrack? = null

    private fun createAudioVideoAndDataTracks() {
        if (localAudioTrack == null)
            localAudioTrack = LocalAudioTrack.create(context, true)
        if (localDataTrack == null)
            localDataTrack = LocalDataTrack.create(context)
        if (localVideoTrack == null) {
            localVideoTrack = LocalVideoTrack.create(context, true, cameraCapturer)
        }
        // In case we resume and are already connected to a room, publish track
        localVideoTrack?.let { localParticipant?.publishTrack(it) }
    }

    private fun destroyVideoTrack() {
        localVideoTrack?.let { localParticipant?.unpublishTrack(it) }
        localVideoTrack?.release()
        localVideoTrack = null
        setLocalVideoRenderer(null)
    }

    fun sendMessage(message : String) {
        localDataTrack?.send(message)
    }

    private fun destroyAllTracks() {
        destroyVideoTrack()
        localAudioTrack?.release()
        localDataTrack?.release()
        localAudioTrack = null
        localDataTrack = null
    }

    private var previousAudioMode = 0
    private var previousMicrophoneMute = false

    private fun configureAudio(enable: Boolean) {
        with(audioManager) {
            if (enable) {
                previousAudioMode = audioManager.mode
                // Request audio focus before making any device switch
                requestAudioFocus()
                /*
                 * Use MODE_IN_COMMUNICATION as the default audio mode. It is required
                 * to be in this mode when playout and/or recording starts for the best
                 * possible VoIP performance. Some devices have difficulties with
                 * speaker mode if this is not set.
                 */
                mode = android.media.AudioManager.MODE_IN_COMMUNICATION
                // Always disable microphone mute during a WebRTC call.
                previousMicrophoneMute = isMicrophoneMute
                isMicrophoneMute = false
            } else {
                mode = previousAudioMode
                abandonAudioFocus(null)
                isMicrophoneMute = previousMicrophoneMute
            }
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { }
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private var room: Room? = null
    private var connectPromise: Promise<Result<Unit>>? = null
    protected var localParticipant: LocalParticipant? = null

    val state: Room.State
        get() {
            return room?.state?:Room.State.DISCONNECTED
        }

    val roomName: String?
        get() {
            return room?.name
        }

    fun connectToRoom(roomName: String): Future<Result<Unit>> = SingleThreadedGlobalScope.asyncFuture {
        if (connectPromise != null) {
            Result.failure<Unit>(RuntimeException("Already connecting"))
        } else {
            connectPromise = Promise()
            var result = Result.success(Unit)
            try {
                createAudioVideoAndDataTracks()
                setLocalVideoRenderer(mainRemoteVideoView)
                configureAudio(true)
                val token = accessTokenGetter.getAccessToken().await()
                val connectOptionsBuilder = ConnectOptions.Builder(token)
                    .dataTracks(Collections.singletonList(localDataTrack))
                    .roomName(roomName)
                localAudioTrack?.let { connectOptionsBuilder.audioTracks(listOf(it)) }
                localVideoTrack?.let { connectOptionsBuilder.videoTracks(listOf(it)) }
                connectOptionsBuilder.preferAudioCodecs(listOf(audioCodec))
                connectOptionsBuilder.preferVideoCodecs(listOf(videoCodec))
                connectOptionsBuilder.encodingParameters(encodingParameters)
                connectOptionsBuilder.enableAutomaticSubscription(enableAutomaticSubscription)
                Video.setLogLevel(LogLevel.OFF)
                room = Video.connect(context, connectOptionsBuilder.build(), this@RoomHandler)
                connectPromise?.let {
                    it.future.await()
                    result = it.future.value
                }
                if (result.isFailure) {
                    destroyTracksAndCleanRoom()
                }
            } catch (e: Exception) {
                destroyTracksAndCleanRoom()
                result = Result.failure(e)
            }
            connectPromise = null
            result
        }
    }

    fun disconnect() {
        room?.disconnect()
    }

    fun pauseVideo() {
        /*
        * Release the local video track before going in the background. This ensures that the
        * camera can be used by other applications while this app is in the background.
        *
        * This does not stop the audio track, and the call continues (allowing to continue the
        * conversation in the background, as in some android apps)
        */
        localVideoRendererBeforePause = currentLocalVideoRenderer
        destroyVideoTrack()
    }

    fun resume() {
        if (room != null) {
            createAudioVideoAndDataTracks()
            setLocalVideoRenderer(localVideoRendererBeforePause)
            localVideoRendererBeforePause = null
        }
    }

    fun isConnecting(): Boolean {
        return room?.let { it.state == Room.State.RECONNECTING}?: false
    }

    private fun destroyTracksAndCleanRoom() {
        destroyAllTracks()
        configureAudio(false)
        room = null
        localParticipant = null
        currentLocalVideoRenderer = null
        localVideoRendererBeforePause = null
    }

    private var currentLocalVideoRenderer : VideoRenderer? = null
    private var localVideoRendererBeforePause : VideoRenderer? = null

    private fun setLocalVideoRenderer(renderer: VideoRenderer?) {
        if (currentLocalVideoRenderer != renderer) {
            currentLocalVideoRenderer?.let {
                localVideoTrack?.removeRenderer(it)
            }
            renderer?.let {
                localVideoTrack?.addRenderer(it)
            }
            currentLocalVideoRenderer = renderer
        }
    }

    // Room.Listener

    fun addRemoteParticipantVideoRenderer(remoteParticipant: RemoteParticipant) {
        if (mainRemoteVideoView == null) {
            return
        }
        remoteParticipant.remoteVideoTracks.firstOrNull()?.let { remoteVideoTrackPublication ->
            if (remoteVideoTrackPublication.isTrackSubscribed) {
                remoteVideoTrackPublication.remoteVideoTrack?.let {
                    it.addRenderer(mainRemoteVideoView)
                    setLocalVideoRenderer(localVideoView)
                }
            }
        }
    }

    fun removeRemoteParticipantVideoRenderer(remoteParticipant: RemoteParticipant) {
        if (mainRemoteVideoView == null) {
            return
        }
        remoteParticipant.remoteVideoTracks.firstOrNull()?.let { remoteVideoTrackPublication ->
            if (remoteVideoTrackPublication.isTrackSubscribed) {
                remoteVideoTrackPublication.remoteVideoTrack?.removeRenderer(mainRemoteVideoView)
                setLocalVideoRenderer(mainRemoteVideoView)
            }
        }
    }

    override fun onConnected(room: Room) {
        Log.i(TAG, "onConnected")
        localParticipant = room.localParticipant
        localDataTrack?.let {localDataTrack ->
            localParticipant?.publishTrack(localDataTrack)
        } ?: run {
            Log.w(TAG, "Unexpected: localDataTrack is null!")
        }
        connectPromise?.setValue(Result.success(Unit))

        Log.i(TAG, "onConnected ${room.remoteParticipants.size}")

        room.remoteParticipants.forEach {
            it.setListener(this)
            addRemoteParticipantVideoRenderer(it)
            listener?.onParticipantConnected(it.identity, it.sid)
        }
        //listener?.onConnected()
    }

    override fun onConnectFailure(room: Room, twilioException: TwilioException) {
        Log.i(TAG, "onConnectFailure: $twilioException")
        destroyTracksAndCleanRoom()
        connectPromise?.setValue(Result.failure(twilioException))
        //listener?.onConnectFailure(twilioException)
    }

    override fun onReconnected(room: Room) {
        Log.i(TAG, "onConnected")
        listener?.onReconnected()
    }

    override fun onDisconnected(room: Room, twilioException: TwilioException?) {
        Log.i(TAG, "onDisconnected: $twilioException")
        listener?.onDisconnected(twilioException?.let {RemoteVideoException(it)})
        destroyTracksAndCleanRoom()
        connectPromise?.setCancelled()
    }

    override fun onReconnecting(room: Room, twilioException: TwilioException) {
        Log.i(TAG, "onReconnecting")
        listener?.onReconnecting(RemoteVideoException(twilioException))
    }

    override fun onParticipantConnected(room: Room, remoteParticipant: RemoteParticipant) {
        Log.i(TAG, "onParticipantConnected ${remoteParticipant.identity}")
        remoteParticipant.setListener(this)
        remoteParticipant.dataTracks.forEach {
            Log.d(TAG, "Participant Data track: ${it.dataTrack}")
        }
        remoteParticipant.remoteDataTracks.forEach {
            Log.d(TAG, "Participant Remote data track ${it.isTrackSubscribed} ${it.remoteDataTrack} ${it.dataTrack}")
            if (it.isTrackSubscribed) {
                it.remoteDataTrack?.setListener(this)
            }
        }
        addRemoteParticipantVideoRenderer(remoteParticipant)
        listener?.onParticipantConnected(remoteParticipant.identity, remoteParticipant.sid)
    }

    override fun onParticipantDisconnected(room: Room, remoteParticipant: RemoteParticipant) {
        Log.i(TAG, "onParticipantDisconnected ${remoteParticipant.identity}")
        removeRemoteParticipantVideoRenderer(remoteParticipant)
        listener?.onParticipantDisconnected(remoteParticipant.identity, remoteParticipant.sid)
    }

    override fun onRecordingStarted(room: Room) {}
    override fun onRecordingStopped(room: Room) {}

    // LoggingParticipantListener

    override fun onDataTrackSubscribed(remoteParticipant: RemoteParticipant,
                                       remoteDataTrackPublication: RemoteDataTrackPublication,
                                       remoteDataTrack: RemoteDataTrack) {
        super.onDataTrackSubscribed(remoteParticipant, remoteDataTrackPublication, remoteDataTrack)
        remoteDataTrack.setListener(this)
    }

    override fun onVideoTrackSubscribed(remoteParticipant: RemoteParticipant,
                                        remoteVideoTrackPublication: RemoteVideoTrackPublication,
                                        remoteVideoTrack: RemoteVideoTrack) {
        super.onVideoTrackSubscribed(remoteParticipant, remoteVideoTrackPublication, remoteVideoTrack)
        mainRemoteVideoView?.let { remoteVideoTrack.addRenderer(it) }
        setLocalVideoRenderer(localVideoView)
        listener?.onParticipantVideoTrackSubscribed(remoteParticipant.identity, remoteParticipant.sid)
    }

    override fun onVideoTrackUnsubscribed(remoteParticipant: RemoteParticipant,
                                          remoteVideoTrackPublication: RemoteVideoTrackPublication,
                                          remoteVideoTrack: RemoteVideoTrack) {
        super.onVideoTrackUnsubscribed(remoteParticipant, remoteVideoTrackPublication, remoteVideoTrack)
        mainRemoteVideoView?.let { remoteVideoTrack.removeRenderer(it) }
        setLocalVideoRenderer(mainRemoteVideoView)
        listener?.onParticipantVideoTrackUnsubscribed(remoteParticipant.identity, remoteParticipant.sid)
    }

    override fun onVideoTrackSubscriptionFailed(remoteParticipant: RemoteParticipant,
                                                remoteVideoTrackPublication: RemoteVideoTrackPublication,
                                                twilioException: TwilioException) {
        super.onVideoTrackSubscriptionFailed(remoteParticipant, remoteVideoTrackPublication, twilioException)
    }

    // RemoteDataTrack.Listener

    override fun onMessage(remoteDataTrack: RemoteDataTrack, messageBuffer: ByteBuffer) {
        Log.i(TAG, "onMessageByte: $messageBuffer")
    }

    override fun onMessage(remoteDataTrack: RemoteDataTrack, message: String) {
        Log.i(TAG, "onMessage: $message")
        listener?.onMessage(remoteDataTrack.name, message)
    }

}