# Pepper Telepresence Libraries and Quickstarts for Android

## Overview

These applications should give you a ready-made starting point for writing your
own telepresence apps with the Pepper robot and Twilio Video service.
These quickstarts are based on the Twilio Quickstart available here:
[https://github.com/twilio/video-quickstart-android](https://github.com/twilio/video-quickstart-android)

## Prerequisite

* **Twilio SDK**: These applications use the [Twilio Video SDK](https://www.twilio.com/video) under the hood to establish the audio/video connection between Pepper and a web app. Follow these [guidelines](../doc/twilio_setup.md) to setup your Twilio account.

## Remote Video or Remote Control

We provide two quickstarts that address the following uses cases:

- *app-telepresence* demonstrates bidirectional audio/video call. It allows a remote person using a web app to communicate with someone in front of Pepper.
- *app-remote-control* demonstrates unidirectional audio/video with command sending. It allows an operator to drive Pepper around an area.

We also provide two libraries:

- *remotevideo*, for creating a Twilio session with Pepper.
- *pepper-telepresence-control*, for interpreting commands on Pepper.


## Running the sample application with a temporary access token

To get started with either one of the sample applications (*app-telepresence* or *app-remote-control*) follow these steps:

1. Open this project in Android Studio and select either the _app-telepresence_ module or the _app-remote-control_ one.

2. [Retrieve a Twilio Access Token](../doc/twilio_setup.md#retrieve-a-temporary-twilio-access-token
) (These tokens have a short lifetime of 1 hour. You will have to retrieve another token once 1 hour has elapsed).

3. Add the access token string copied from the console to a variable named `TWILIO_ACCESS_TOKEN`
in your **local.properties** file.

     ```
     TWILIO_ACCESS_TOKEN=abcdef0123456789
     ```

4. Run the chosen quickstart app on an Android device or Android emulator.

5. Type a room name in the room name field to connect to a Room. The room name must be at least 20 characters.

6. Install the [Pepper Telepresence Quickstart for JavaScript](../web/README.md) and connect to the same room.

## Retrieving Twilio Tokens from the REST api deployed on Firebase

Its not really convenient to manually retrieve short lived Twilio Access Tokens and copy paste them in the *local.properties* file.
The [Pepper Telepresence Web App](../web/README.md) contains a REST API that can be used to provide _Access Tokens_ to the Android app, using HTTP calls, as you would do in a real world deployment. If you deploy the [Pepper Telepresence Web App](../web/README.md) to [Firebase](https://firebase.google.com/) and want the Android app to use it, then go to the [Firebase console](https://console.firebase.google.com/), and follow the Android setup ([https://firebase.google.com/docs/android/setup](https://firebase.google.com/docs/android/setup)):

- Add an android project to your firebase project
- Download the *google-service.json* file and place it in the app folder.
- Uncomment the following line in the app/build.gradle
    ```
    apply plugin: 'com.google.gms.google-services'
    ```

Then rebuild and relaunch the Android app on Pepper, and it will now retrieve Twilio Access Tokens from the server hosted in firebase.

## The *remotevideo* library

The *remotevideo* library included in this project, uses Twilio SDK under the hood, and allows to create Twilio sessions with Pepper. Use it in your Pepper Android apps to exchange audio and video and commands with web or mobile app or other Peppers.

### Add the libraries as a dependency

To add the library to your project, follow these [instructions](https://jitpack.io/#softbankrobotics-labs/Pepper-Telepresence-Toolkit).

### Library Usage

#### PepperCameraCapturer

We provide a class allowing to stream Pepper's Top camera. This class is called `PepperCameraCapturer`. It merges the Tablet Camera and Pepper Head Top Camera into one single `VideoCapturer`, that you can use with the Twilio SDK.

Here is an excerpt of the class with the main functions of interest, beside the one inherited (and overridden) from `VideoCapturer`:
```
class PepperCameraCapturer(context: Context) {

    // Switch between Tablet (default) and Top camera.
    // When app lose Pepper Focus, Tablet camera will be used even if Top camera is selected.
    // When app regain Pepper Focus, Top camera will be used if it was previously selected.
    fun switchCamera()

    // Mandatory: Call this function in your activity RobotLifeCycle onRobotFocusGained callback
    fun onRobotFocusGained(qiContext: QiContext)

    // Mandatory: Call this function in your activity RobotLifeCycle onRobotFocusLost callback
    fun onRobotFocusLost()

    [...]
}
```

`PepperCameraCapturer` can be used with the `RoomHandler` class described hereafter.

#### RoomHandler

The `RoomHandler` class wraps the Twilio SDK room and allows you to quickly set up videoconferencing in your app. Here is an excerpt of the class with the main functions:

```
class RoomHandler(val context: Context,
                  val accessTokenGetter: AccessTokenGetter,
                  val audioManager: AudioManager,
                  val cameraCapturer: CameraCapturer,
                  val videoView: VideoView,
                  val listener: Listener? = null) {

    // Implement this interface to provide a Twilio Access Token to the RoomHandler
    interface AccessTokenGetter {
        // Return a future to a string containing a valid Twilio Access Token
        fun getAccessToken(): Future<String>
    }

    // Implement this interface to get feedback on the RoomHandler state
    interface Listener {
        fun onReconnecting(exception: RemoteVideoException)
        fun onReconnected()
        fun onDisconnected(exception: RemoteVideoException?)
        fun onParticipantConnected(participantIdentity: String, participantSid: String)
        fun onParticipantDisconnected(participantIdentity: String, participantSid: String)
        // Will be called whenever a remote peer send a message to the robot.
        // We use it to send control commands (switch between pepper camera, go forward, turn, lookat)
        fun onMessage(trackName: String, message: String)
    }

    // The state of the Twilio Video Room
    val state: Room.State

    // If RoomHandler is connected, contains the name of the videoconference room
    val roomName: String?

    // If RoomHandler is not already connected, allow to connect to videoconference room with name
    // roomName.
    // Returns a Future to a Result, which contains a typed exception in case of failure.
    fun connectToRoom(roomName: String): Future<Result<Unit>>

    // Disconnect from a videoconference room if connected
    fun disconnect()

    // Mandatory: Call this in your activity onPause()
    // If RoomHandler is connected, it will stop streaming the video, and the call will continue
    // with sound only.
    fun pauseVideo()

    // Mandatory: Call this in your activity onResume()
    // If RoomHandler is connected, it will resume streaming the video
    fun resume()

    // Return true if RoomHandler is currently in the process of (re)connecting to a room
    fun isConnecting()

    // Send a message to the remote peer.
    fun sendMessage(message: String)
}
```

### Going further

The classes we provide allow you to quickly create an app with basic telepresence functionalities on Pepper. However we do not intend to cover all the possible features of a telepresence application.
For instance, we do not handle multiple participants for now. If you want to extend further your telepresence application, we advise that you keep and use the `PepperCameraCapturer` code as it is, but copy the `RoomHandler` class and extend it further to fit your needs. You will also have to read the Twilio SDK documentation, available [here](https://www.twilio.com/docs/video/android).

## The *pepper-telepresence-control* library

The *pepper-telepresence-control* library can be used in your Telepresence Android apps, to interpret commands send by a remote peer to Pepper.

### Add the libraries as a dependency

To add the library to your project, follow these [instructions](https://jitpack.io/#softbankrobotics-labs/Pepper-Telepresence-Toolkit).

### Library Usage

Initialise the QiSDK in `onCreate`. If you are unsure how to do this, refer to the QiSDK tutorials [here](https://developer.softbankrobotics.com/pepper-qisdk/getting-started)
```
QiSDK.register(this, this)
```
In `onRobotFocusGained`, instantiate a `TelepresenceRobotController` object by passing it the QiContext and start it:
```
telepresenceRobotController = TelepresenceRobotController(qiContext)
telepresenceRobotController.start()
```
Send JSON commands to the library using the `handleJsonCommand` method:
```
telepresenceRobotController.handleJsonCommand("{\"cmd\":\"move_forward\"}")
```
You can send commands to the library to:
* Say a text
* Move forwards / backwards
* Move the tablet
* Move the head
* Make the robot strafe
* Make the robot turn

To make the robot say a text, send this JSON:
```
{
  "cmd":"say_text",
  "args":"Text to say"
}
```
To make the robot move forwards, send this JSON:
```
{
  "cmd":"move_forward"
}
```
The robot will move forwards for 600ms, so be sure to send the command every 500ms for instance and stop sending it when you want the robot to stop.

To make the robot move its tablet, send this JSON:
```
{
  "cmd":"tablet_look_at",
  "args":[X, Y]
} 
```
Replace X and Y with float values depending on where you want the tablet to move:
* Up: Y=1
* Down: Y=-1
* Right: X=1
* Left: X=-1

To make the robot move its head, send this JSON:
```
{
  "cmd":"head_look_at",
  "args":[X, Y]
} 
```
Replace X and Y with float values, see previous command to define them.

To make the robot strafe, send this JSON:
```
{
  "cmd":"strafe",
  "args":[X, Y]
} 
```
Replace X and Y with float values, see previous command to define them.

To make the robot turn, send this JSON:
```
{
  "cmd":"turn",
  "args":X
} 
```
Replace X by the value of the rotation angle in deg, for example 90 or -90.

Stop the `TelepresenceRobotController` in `onRobotFocusLost`:
```
telepresenceRobotController.stop()
```

#### Make Pepper automatically look down when moving

You might want Pepper to look down when it's moving as it can allow you do see obstacles if you're using the head camera stream.

You can do so with the library by setting a boolean when instantiating the `TelepresenceRobotController` object:
```
telepresenceRobotController = TelepresenceRobotController(qiContext, true)
```
The robot will then look down when running `move_forwards` and `strafe` actions.