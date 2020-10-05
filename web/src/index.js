'use strict';

var jws = require('jws');

var username;
var roomInput;
var identity;
var roomName;
var TWILIO_TOKEN;
var translationsAvailable = false;
var secret;

if (useFirebase == false) {
  // Put below twilio token generated on https://www.twilio.com/console/video/project/testing-tools
  TWILIO_TOKEN = "eyXXXX.eyXXXX.YYYY";
}

function isString (value) {
  return typeof value === 'string' || value instanceof String;
}
var operatorMode = getUrlParameter("mode") == "operator" ? true : false;

//---------------------------------
// Communication with child iframe
// --------------------------------

window.addEventListener("message", function(event) {
  // Our frame are sandboxed, origin should be "null"
  if (event.origin!="null")
    return;
  onMessageReceived(event);
}, false);

onMessageReceivedHandler = function (msg) {
  if (msg.cmd) {
    // Called when login is done. We get the secret and then remove the iframe with the original secret.
    // We now are the only keeper of the secret.
    if (msg.cmd == "translate" && isString(msg.value)) {
      return getTranslation(msg.value);
    } else if (msg.cmd == 'isFirebaseEnabled') {
      return isFirebaseEnabled();
    } else if (msg.cmd == 'joinRoom' && isString(msg.username) && isString(msg.roomname)) {
      return joinRoom(msg.username, msg.roomname);
    } else if (msg.cmd == 'isTranslationAvailable') {
      return isTranslationAvailable();
    } else if (msg.cmd == 'isOperatorMode') {
      return isOperatorMode();
    } else if (msg.cmd == 'getRoomName') {
      return getRoomName();
    } else if (msg.cmd == 'leaveRoom') {
      return leaveRoom();
    } else if (msg.cmd == 'useHeadCamera') {
      return useHeadCamera();
    } else if (msg.cmd == 'useTabletCamera') {
      return useTabletCamera();
    } else if (msg.cmd == 'moveForward') {
      return moveForward();
    } else if (msg.cmd == 'turn' && Number.isInteger(msg.angle)) {
      return turn(msg.angle);
    } else if (msg.cmd == 'strafe' && Number.isInteger(msg.x) && Number.isInteger(msg.y)) {
      return strafe(msg.x, msg.y);
    } else if (msg.cmd == 'tabletLookAt' && Number(msg.x) == msg.x && Number(msg.y) == msg.y) {
      return tabletLookAt(msg.x, msg.y);
    } else if (msg.cmd == 'headLookAt' && Number(msg.x) == msg.x && Number(msg.y) == msg.y) {
      return headLookAt(msg.x, msg.y);
    } else if (msg.cmd == 'firebaseSignIn' && isString(msg.login) && isString(msg.password) && isString(msg.roomname)) {
      return firebaseSignIn(msg.login, msg.password, msg.roomname);
    }
  }
  return false;
}

//-------------------
// Child iframe APIs
// ------------------

function reverseString(str){
  let stringRev ="";
  for(let i= 0; i<str.length; i++){
    stringRev = str[i]+stringRev;
  }
  return stringRev;
}

function getTranslation(string) {
  return $.i18n(string);
}

function joinRoom(username, roomname) {
  document.getElementById("overpanel").style.display = 'block';
  let seed = roomname;
  secret = (md5(roomname) + md5(reverseString(roomname)));
  roomName = sha512(seed).substring(0, 20);
  document.getElementById("ifr").src = "room.html#" + document.location.origin;
  joinTwilioRoom(roomName, getTwilioToken())
}

function isFirebaseEnabled() {
  return useFirebase;
}

function isTranslationAvailable() {
  return translationsAvailable;
}

function isOperatorMode() {
  console.log("IS OPERATOR MODE " + operatorMode);
  return operatorMode;
}

function getRoomName() {
  return roomName;
}

function leaveRoom() {
  disconnect();
  if (useFirebase)
    firebaseSignOut();
  document.getElementById("overpanel").style.display = 'none';
  document.getElementById("ifr").src = "login.html#" + document.location.origin;
}

function getTwilioToken() {
  return TWILIO_TOKEN;
}

function useHeadCamera() {
  sendMessage(JSON.stringify({ "cmd": "use_head_camera" }));
}

function useTabletCamera() {
  sendMessage(JSON.stringify({ "cmd": "use_tablet_camera" }));
}

function turn(angle) {
  sendMessage(JSON.stringify({ "cmd": "turn", "args": [angle] }));
}

function moveForward() {
  sendMessage(JSON.stringify({ "cmd": "move_forward", "args": [] }));
}

function strafe(x, y) {
  sendMessage(JSON.stringify({ "cmd": "strafe", "args": [x, y] }));
}

function headLookAt(x, y) {
  sendMessage(JSON.stringify({ "cmd": "head_look_at", "args": [x, y] }));
}

function tabletLookAt(x, y) {
  sendMessage(JSON.stringify({"cmd": "tablet_look_at", "args": [x, y]}));
}


// -------------------
// Parent window APIs
// -------------------

function blurControls(enabled) {
  let guiframe = document.getElementById("ifr").contentWindow;
  return postMessage(guiframe, { cmd: "blurControls", enabled: enabled });
}

function setVideoOnClick() {
  if (operatorMode) {
    let guiframe = document.getElementById("ifr").contentWindow;
    return postMessage(guiframe, { cmd: "setVideoOnClick" });
  }
}


/****************
* Firebase code
*****************/

async function firebaseSignIn(login, password, roomname) {
  if (login != '' && password != '') {
    try {
      let seed = roomname;
      secret = (md5(roomname) + md5(reverseString(roomname)));
      roomName = sha512(seed).substring(0, 20);
      username = login;
      await firebase.auth().signInWithEmailAndPassword(login, password);
      return true;
    }
    catch(error) {
      return "Firebase signIn error: " + error.message;
    }
  } else {
    return $.i18n('enter-login-password');
  }
}

function firebaseSignOut() {
  firebase.auth().signOut().then(function() {
    // Sign-out successful.
    console.log("Signout successfull");
  }).catch(function(error) {
    // An error happened.
    console.log("Signout error: " + error);
  });
}

function firebaseSubscribeToAuth() {
  // Subscribe to user logged in after dialog gets opened.
  firebase.auth().onAuthStateChanged(function(user) {
    if (user) {
      // User is signed in.
      user.getIdToken().then(function(token) {
        document.getElementById("overpanel").style.display = 'block';
        document.getElementById("ifr").src = "room.html";
        retrieveTwilioTokenFromFirebaseFunctionAndStart(username, roomName, token);
      });
    } else {
    }
  });
}

function retrieveTwilioTokenFromFirebaseFunctionAndStart(username, roomname, firebaseToken) {
  // Obtain a token from the server in order to connect to the Room.
  $.ajax({
    url: '/token',
    method: 'POST',
    dataType: 'json',
    crossDomain: false,
    contentType: "application/json; charset=utf-8",
    beforeSend: function (xhr) {
      xhr.setRequestHeader ("Authorization", "Bearer " + firebaseToken);
    },
    data: JSON.stringify({'data': {'identity':username}}),
    error: function (jqXHR, textStatus, errorThrown) {
      alert("Error retrieving Twilio token: " + errorThrown);
    },
    success: function (data) {
      joinTwilioRoom(roomname, data.result.token)
    }
  });
}

// -------
// Twilio
// -------

var Video = require('twilio-video');

var activeRoom;
var videoTracks;
var previewTracks;
var oncall = false;

// Attach the Track to the DOM.
function attachTrack(track, container) {
  try {
    container.appendChild(track.attach());
  } catch (e) {
    console.log(e);
  }
}

// Attach array of Tracks to the DOM.
function attachTracks(tracks, container) {
  tracks.forEach(function (track) {
    attachTrack(track, container);
  });
}

// Detach given track from the DOM.
function detachTrack(track) {
  try {
    track.detach().forEach(function (element) {
      element.remove();
    });
  } catch (e) {
    console.log(e);
  }
}

// A new RemoteTrack was published to the Room.
function trackPublished(publication, container) {
  if (publication.isSubscribed) {
    attachTrack(publication.track, container);
    if (publication.track.kind == "video")
      setVideoOnClick();
  }
  publication.on('subscribed', function (track) {
    console.log('Subscribed to ' + publication.kind + ' track');
    attachTrack(track, container);
    if (publication.track.kind == "video") {
      setVideoOnClick();
    } else if (publication.track.kind == "data") {
      // If we wanted to subscribe to messages sent from the robot
      // it would be here.
      //publication.track.on('message', data => {});
    }
  });
  publication.on('unsubscribed', detachTrack);
}

// A RemoteTrack was unpublished from the Room.
function trackUnpublished(publication) {
  console.log(publication.kind + ' track was unpublished.');
}

// A new RemoteParticipant joined the Room
function participantConnected(participant, container) {

  blurControls(false);
  let selfContainer = document.getElementById("participantContainer")
  participant.tracks.forEach(function (publication) {
    trackPublished(publication, selfContainer);
  });
  participant.on('trackPublished', function (publication) {
    trackPublished(publication, selfContainer);
  });
  participant.on('trackUnpublished', trackUnpublished);
}

// Detach the Participant's Tracks from the DOM.
function detachParticipantTracks(participant) {
  var tracks = getTracks(participant);
  tracks.forEach(detachTrack);
}

// Create the command tracks
const dataTrack = new Video.LocalDataTrack({
  "name": "commands",
  "ordered": true,
  "maxRetransmits": 0
});
const dataTrackPublished = {};

dataTrackPublished.promise = new Promise((resolve, reject) => {
  dataTrackPublished.resolve = resolve;
  dataTrackPublished.reject = reject;
});

function sendMessage(messageString) {
  console.log("Preparing to send " + messageString);
  const signedMessage = jws.sign({
    header: { alg: 'HS256' },
    payload: btoa(messageString),
    secret: secret,
  });
  dataTrack.send(signedMessage)
  console.log("Sent signed message", JSON.stringify(signedMessage));
}

// When we are about to transition away from this page, disconnect
// from the room, if joined.
window.addEventListener('beforeunload', leaveRoomIfJoined);


function disconnect() {
  oncall = false;
  console.log("DISCONNECT");
  console.log(activeRoom);
  if (activeRoom)
    activeRoom.disconnect();
}

function joinTwilioRoom(roomname, token) {
  if (oncall) {
    return;
  }
  oncall = true;

  console.log("Joining room '" + roomname + "'...");
  var connectOptions = {
    name: roomname,
    logLevel: 'debug',
    tracks: [dataTrack],
  };

  if (!operatorMode) {
    // Send the local video track
    Video.createLocalTracks().then(localTracks => {
      window.videoTracks = videoTracks = localTracks;
      var tracks = localTracks.slice();
      tracks.push(dataTrack);
      connectOptions.tracks = tracks;
      Video.connect(token, connectOptions).then(roomJoined, function (error) {
        oncall = false;
        if (videoTracks) {
          videoTracks.forEach(function (track) {
            track.stop();
          });
          videoTracks = null;
        }
        alert('Could not connect: ' + error.message);
        leaveRoom();
      });
    });
  } else {
    // Just connect without creating a local video track
    Video.connect(token, connectOptions).then(roomJoined, function (error) {
      oncall = false;
      alert('Could not connect: ' + error.message);
      leaveRoom();
    });
  }
}

// Get the Participant's Tracks.
function getTracks(participant) {
  return Array.from(participant.tracks.values()).filter(function (publication) {
    return publication.track;
  }).map(function (publication) {
    return publication.track;
  });
}
// Successfully connected!
function roomJoined(room) {


  document.getElementById('participantContainer').innerHTML
    = getTranslation('waiting-for-participant')
  window.room = activeRoom = room;

  // Attach LocalParticipant's Tracks, if exists and not already attached.
  var previewContainer = document.getElementById('local-media');
  if (previewContainer && !previewContainer.querySelector('video')) {
    attachTracks(getTracks(room.localParticipant), previewContainer);
  }

  // Attach the Tracks of the Room's Participants.
  var remoteMediaContainer = document.getElementById('remote-media');
  room.participants.forEach(function (participant) {
    console.log("Already in Room: '" + participant.identity + "'");
    document.getElementById('participantContainer').innerHTML = "";
    participantConnected(participant, remoteMediaContainer);
  });

  // When a Participant joins the Room, log the event.
  room.on('participantConnected', function (participant) {
    console.log("Joining: '" + participant.identity + "'");
    document.getElementById('participantContainer').innerHTML = "";
    participantConnected(participant, remoteMediaContainer);
  });

  // When a Participant leaves the Room, detach its Tracks.
  room.on('participantDisconnected', function (participant) {
    console.log("RemoteParticipant '" + participant.identity + "' left the room");
    detachParticipantTracks(participant);
    blurControls(true);
    document.getElementById('participantContainer').innerHTML = $.i18n('waiting-for-participant');

    //removeName(participant);
  });

  // Once the LocalParticipant leaves the room, detach the Tracks
  // of all Participants, including that of the LocalParticipant.
  room.on('disconnected', function () {
    blurControls(true);
    if (videoTracks) {
      videoTracks.forEach(function (track) {
        track.stop();
      });
      videoTracks = null;
    }
    detachParticipantTracks(room.localParticipant);
    room.participants.forEach(detachParticipantTracks);
    //room.participants.forEach(removeName);
    oncall = false;
    activeRoom = null;
  });


  room.localParticipant.on('trackPublished', publication => {
    console.log("***********************");
    console.log(publication.track);
    console.log(dataTrack);
    if (publication.track === dataTrack) {
      dataTrackPublished.resolve();
    }
  });

  room.localParticipant.on('trackPublicationFailed', (error, track) => {
    console.log("*********************** AIE");
    console.log(error);
    console.log(track);
    console.log(dataTrack);
    if (track === dataTrack) {
      dataTrackPublished.reject(error);
    }
  });
}



// Leave Room.
function leaveRoomIfJoined() {
  leaveRoom();
}



/**************
* Common code
***************/

function onReady() {
  console.log("On Ready");
  loadTranslations().done(function() {
    translationsAvailable = true;
    applyTranslations();
  }).done(function () {

    console.log("Operator mode: " + operatorMode);
    if (useFirebase) {
      firebaseSubscribeToAuth();
    }
    if (operatorMode) {
      document.getElementById('local-media').style.display = "none";
    }
  })
}


$(document).ready(function() {
  console.log("Document ready");
  if (useFirebase) {
    firebaseSignOut();
    onReady();
  } else {
    onReady();
  }
});
