package com.softbankrobotics.remotevideo.exceptions

import com.twilio.video.TwilioException
import java.lang.Exception


class RemoteVideoException internal constructor(val twilioException: TwilioException)
    : Exception(twilioException) {

}