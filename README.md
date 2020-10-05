# Pepper Telepresence Toolkit

## Documents Contents

#### Project READMEs

These documents are for the Android and Web parts of the library:

1. [Android Application](android/README.md) 
2. [Web Application](web/README.md) 

#### Additional

These additional documents explain the various security methods we have implemented into the solution, aswell as setup guides for Firebase and Twilio: 

1. [Security Overview](doc/security.md)
2. [Firebase Setup](doc/firebase_setup.md)
3. [Twilio Setup](doc/twilio_setup.md)

## Overview

This project contains some elements to help build a Telepresence / Teleoperation solution on Pepper QiSDK.

It contains two libraries:

 * **remotevideo**, for creating a Twilio session with Pepper
 * **pepper-telepresence-control**, for interpreting commands on Pepper.

In addition, it also provides sample applications using those:

 * **Simple Telepresence**, for a two-way video & text communication through the robot (no robot movement), only using the remotevideo library
 * **Remote Control**: allowing an operator to drive Pepper around an area, using both libraries

To work, these samples require a web part, that is also provided (for simplicity’s sake, both use the same web back-end).

Using these samples both require a Twilio account (and Firebase to test for more than an hour).

## Running the samples

There are two ways of running the samples

 * Locally, for a quick test
 * With the server hosted on firebase

### Running with a local server

For testing quickly, you can use temporary Twilio access tokens that will only be valid for one hour; this allows you to run your server locally without needing a firebase account.

 * Setup Twilio: see [Twilio setup](doc/twilio_setup.md)
 * Generate a temporary Twilio access token for the web server
 * Run the web server locally as explained in the [Web App documentation](web/README.md)
 * Generate a temporary Twilio access token for the robot application (with a different client identity!)
 * Configure the android application with the Twilio access token "Running the sample application with a temporary access token" [Pepper Telepresence Library and Quickstart for Android](android/README.md)
 * Build and install the android application.
 * Enter the same passphrase on both the web interface and the andoid application

Once the tokens expire, you will need to generate new ones, restart the server and rebuild a new android application.

### Running with a server hosted on Firebase

This is a more viable long-term solution, in which the server code (handling the operator GUI as well as the Twilio token generation) is hosten on Firebase. It will require a paying firebase accoutn.

 * Setup Twilio (same as previously) see the doc on [Twilio setup](doc/twilio_setup.md)
 * Retrieve the Twilio configuration ids for the web server
 * Create a Firebase project - see [Firebase setup](doc/firebase_setup.md)
 * Configure the web client and deploy to firebase as explained in the [Web App documentation](web/README.md)
 * Configure the desired android application for Firebase as explained in [the Android app documentation](android/README.md)
 * Run the android application on Pepper
 * Enter the same passphrase on both the web interface and the andoid application

## Building your own solution

Chances are, the two samples do not exactly meet your needs; but you can use the same components to build your own solution (both server-side, and client-side, on Pepper)

Some things that can vary from one case to another:

 * Data Streaming: What information should be sent between the robot and the remote operator? What kind of data? Audio, Video, Commands? What direction? From Pepper to the Operator, or from the Operator to Pepper?
 * Authentication: Who is allowed to connect to which robots? How do they authenticate?
 * Multi-person calls: What happens if several people try to take control of Pepper at the same time?

Now let’s go over these aspects:

### Data Streaming

This is handled by Twilio, which offers a lot of flexibility in terms of which data can be transferred.

There are two possibilities for sending video data from Pepper:

 * Sending Pepper’s head video: this has the advantage of providing a better viewpoint that can see in more viewpoints, but has a lower framerate
 * Sending Pepper’s tablet video: this has a better framerate, but is at a fixed, upwards-fixing angle 

The samples show examples of different kinds of data exchanged:
 * In the telepresence sample, video and audio data is exchanged both ways, using Pepper’s tablet camera (as the higher framerate works better)
 * In the driving sample, audio and video data are only sent one way, from Pepper to the operator, with an option to switch between pepper’s head camera (the default) and tablet camera

Depending on the situation, you may want to exchange different data (for example, only exchange audio, or combine driving with full two-way data exchange).

The examples all rely on audio streaming, but one could also send messages to make Pepper speak, or have Pepper send back the transcription of what users have said.

It would also be possible to add extra commands to make Pepper play animations, show images, dance, create a map, etc.

### Authentication

One can imagine several scenarios by which someone could connect to Pepper

 * A “robot call center” in which several operators can connect to any of a fleet of robots
 * Robots in stores, where each store manager is allowed to connect to his own Pepper
 * Robots to which members of the public can connect (for example, to speak to their family through a robot in a hospital)

In the provided samples, users authenticate both on the robot and the web side, but (as in the first “call center scenario”) there is no extra assignment between users and robots; that would have to be managed server-side.

### Multi-person calls

These samples all rely on Twilio in P2P mode, so only two person can connect to a given room. Any third person attempting to join will be kicked.

It would be possible to use Twilio to have more than two people participate in a call with Pepper - this would require both configuring Twilio differently, and reworking the robot-side and web-side GUIs to handle the display of more people.

## Caring about security

Telepresence applications are sensitive applications in term of security.
If a hacker manage to break your application security, it could steal your user data, spy on them or even worse, harm them, as some telepresence scenarii involves controlling a Pepper robot that can interact with the real world.
To prevent this, the security of your apps must be high.
In this project we put in place several measure in order to secure the application, and limit the harm hackers could do. Refer to [this page](doc/security.md) for a complete list of these measure.

## License

This project is licensed under the BSD 3-Clause "New" or "Revised" License - see the [COPYING](COPYING.md) file for details.
