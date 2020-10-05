package com.softbankrobotics.remotevideo.calls

import android.util.Log
import com.softbankrobotics.helpers.TAG
import com.twilio.video.RemoteAudioTrack
import com.twilio.video.RemoteAudioTrackPublication
import com.twilio.video.RemoteDataTrack
import com.twilio.video.RemoteDataTrackPublication
import com.twilio.video.RemoteParticipant
import com.twilio.video.RemoteVideoTrack
import com.twilio.video.RemoteVideoTrackPublication
import com.twilio.video.TwilioException

abstract class LoggingParticipantListener : RemoteParticipant.Listener   {

    override fun onAudioTrackPublished(remoteParticipant: RemoteParticipant,
                                       remoteAudioTrackPublication: RemoteAudioTrackPublication
    ) {
        Log.i(TAG, "onAudioTrackPublished: " +
                "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                "[RemoteAudioTrackPublication: sid=${remoteAudioTrackPublication.trackSid}, " +
                "enabled=${remoteAudioTrackPublication.isTrackEnabled}, " +
                "subscribed=${remoteAudioTrackPublication.isTrackSubscribed}, " +
                "name=${remoteAudioTrackPublication.trackName}]")
    }

    override fun onAudioTrackUnpublished(remoteParticipant: RemoteParticipant,
                                         remoteAudioTrackPublication: RemoteAudioTrackPublication
    ) {
        Log.i(TAG, "onAudioTrackUnpublished: " +
                "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                "[RemoteAudioTrackPublication: sid=${remoteAudioTrackPublication.trackSid}, " +
                "enabled=${remoteAudioTrackPublication.isTrackEnabled}, " +
                "subscribed=${remoteAudioTrackPublication.isTrackSubscribed}, " +
                "name=${remoteAudioTrackPublication.trackName}]")
    }

    override fun onDataTrackPublished(remoteParticipant: RemoteParticipant,
                                      remoteDataTrackPublication: RemoteDataTrackPublication
    ) {
        Log.i(TAG, "onDataTrackPublished: " +
                "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                "[RemoteDataTrackPublication: sid=${remoteDataTrackPublication.trackSid}, " +
                "enabled=${remoteDataTrackPublication.isTrackEnabled}, " +
                "subscribed=${remoteDataTrackPublication.isTrackSubscribed}, " +
                "name=${remoteDataTrackPublication.trackName}]")
    }

    override fun onDataTrackUnpublished(remoteParticipant: RemoteParticipant,
                                        remoteDataTrackPublication: RemoteDataTrackPublication
    ) {
        Log.i(TAG, "onDataTrackUnpublished: " +
                "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                "[RemoteDataTrackPublication: sid=${remoteDataTrackPublication.trackSid}, " +
                "enabled=${remoteDataTrackPublication.isTrackEnabled}, " +
                "subscribed=${remoteDataTrackPublication.isTrackSubscribed}, " +
                "name=${remoteDataTrackPublication.trackName}]")
    }

    override fun onVideoTrackPublished(remoteParticipant: RemoteParticipant,
                                       remoteVideoTrackPublication: RemoteVideoTrackPublication
    ) {
        Log.i(TAG, "onVideoTrackPublished: " +
                "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                "[RemoteVideoTrackPublication: sid=${remoteVideoTrackPublication.trackSid}, " +
                "enabled=${remoteVideoTrackPublication.isTrackEnabled}, " +
                "subscribed=${remoteVideoTrackPublication.isTrackSubscribed}, " +
                "name=${remoteVideoTrackPublication.trackName}]")
    }

    override fun onVideoTrackUnpublished(remoteParticipant: RemoteParticipant,
                                         remoteVideoTrackPublication: RemoteVideoTrackPublication
    ) {
        Log.i(TAG, "onVideoTrackUnpublished: " +
                "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                "[RemoteVideoTrackPublication: sid=${remoteVideoTrackPublication.trackSid}, " +
                "enabled=${remoteVideoTrackPublication.isTrackEnabled}, " +
                "subscribed=${remoteVideoTrackPublication.isTrackSubscribed}, " +
                "name=${remoteVideoTrackPublication.trackName}]")
    }

    override fun onAudioTrackSubscribed(remoteParticipant: RemoteParticipant,
                                        remoteAudioTrackPublication: RemoteAudioTrackPublication,
                                        remoteAudioTrack: RemoteAudioTrack
    ) {
        Log.i(TAG, "onAudioTrackSubscribed: " +
                "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                "[RemoteAudioTrack: enabled=${remoteAudioTrack.isEnabled}, " +
                "playbackEnabled=${remoteAudioTrack.isPlaybackEnabled}, " +
                "name=${remoteAudioTrack.name}]")
    }

    override fun onAudioTrackUnsubscribed(remoteParticipant: RemoteParticipant,
                                          remoteAudioTrackPublication: RemoteAudioTrackPublication,
                                          remoteAudioTrack: RemoteAudioTrack
    ) {
        Log.i(TAG, "onAudioTrackUnsubscribed: " +
                "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                "[RemoteAudioTrack: enabled=${remoteAudioTrack.isEnabled}, " +
                "playbackEnabled=${remoteAudioTrack.isPlaybackEnabled}, " +
                "name=${remoteAudioTrack.name}]")
    }

    override fun onAudioTrackSubscriptionFailed(remoteParticipant: RemoteParticipant,
                                                remoteAudioTrackPublication: RemoteAudioTrackPublication,
                                                twilioException: TwilioException
    ) {
        Log.i(TAG, "onAudioTrackSubscriptionFailed: " +
                "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                "[RemoteAudioTrackPublication: sid=${remoteAudioTrackPublication.trackSid}, " +
                "name=${remoteAudioTrackPublication.trackName}]" +
                "[TwilioException: code=${twilioException.code}, " +
                "message=${twilioException.message}]")
    }

    override fun onDataTrackSubscribed(remoteParticipant: RemoteParticipant,
                                       remoteDataTrackPublication: RemoteDataTrackPublication,
                                       remoteDataTrack: RemoteDataTrack
    ) {
        Log.i(TAG, "onDataTrackSubscribed: " +
                "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                "[RemoteDataTrack: enabled=${remoteDataTrack.isEnabled}, " +
                "name=${remoteDataTrack.name}]")
    }


    override fun onDataTrackUnsubscribed(remoteParticipant: RemoteParticipant,
                                         remoteDataTrackPublication: RemoteDataTrackPublication,
                                         remoteDataTrack: RemoteDataTrack
    ) {
        Log.i(TAG, "onDataTrackUnsubscribed: " +
                "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                "[RemoteDataTrack: enabled=${remoteDataTrack.isEnabled}, " +
                "name=${remoteDataTrack.name}]")
    }

    override fun onDataTrackSubscriptionFailed(remoteParticipant: RemoteParticipant,
                                               remoteDataTrackPublication: RemoteDataTrackPublication,
                                               twilioException: TwilioException
    ) {
        Log.i(TAG, "onDataTrackSubscriptionFailed: " +
                "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                "[RemoteDataTrackPublication: sid=${remoteDataTrackPublication.trackSid}, " +
                "name=${remoteDataTrackPublication.trackName}]" +
                "[TwilioException: code=${twilioException.code}, " +
                "message=${twilioException.message}]")
    }

    override fun onVideoTrackSubscribed(remoteParticipant: RemoteParticipant,
                                        remoteVideoTrackPublication: RemoteVideoTrackPublication,
                                        remoteVideoTrack: RemoteVideoTrack
    ) {
        Log.i(TAG, "onVideoTrackSubscribed: " +
                "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                "[RemoteVideoTrack: enabled=${remoteVideoTrack.isEnabled}, " +
                "name=${remoteVideoTrack.name}]")
    }

    override fun onVideoTrackUnsubscribed(remoteParticipant: RemoteParticipant,
                                          remoteVideoTrackPublication: RemoteVideoTrackPublication,
                                          remoteVideoTrack: RemoteVideoTrack
    ) {
        Log.i(TAG, "onVideoTrackUnsubscribed: " +
                "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                "[RemoteVideoTrack: enabled=${remoteVideoTrack.isEnabled}, " +
                "name=${remoteVideoTrack.name}]")
    }

    override fun onVideoTrackSubscriptionFailed(remoteParticipant: RemoteParticipant,
                                                remoteVideoTrackPublication: RemoteVideoTrackPublication,
                                                twilioException: TwilioException
    ) {
        Log.i(TAG, "onVideoTrackSubscriptionFailed: " +
                "[RemoteParticipant: identity=${remoteParticipant.identity}], " +
                "[RemoteVideoTrackPublication: sid=${remoteVideoTrackPublication.trackSid}, " +
                "name=${remoteVideoTrackPublication.trackName}]" +
                "[TwilioException: code=${twilioException.code}, " +
                "message=${twilioException.message}]")
    }

    override fun onAudioTrackEnabled(remoteParticipant: RemoteParticipant,
                                     remoteAudioTrackPublication: RemoteAudioTrackPublication
    ) {
    }

    override fun onVideoTrackEnabled(remoteParticipant: RemoteParticipant,
                                     remoteVideoTrackPublication: RemoteVideoTrackPublication
    ) {
    }

    override fun onVideoTrackDisabled(remoteParticipant: RemoteParticipant,
                                      remoteVideoTrackPublication: RemoteVideoTrackPublication
    ) {
    }

    override fun onAudioTrackDisabled(remoteParticipant: RemoteParticipant,
                                      remoteAudioTrackPublication: RemoteAudioTrackPublication
    ) {
    }
}