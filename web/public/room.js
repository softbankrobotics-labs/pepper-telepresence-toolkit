

//-----------------------------------
// Communication with Parent window
// ----------------------------------

var origin = document.location.origin;
window.addEventListener("message", function(event) {
  if (event.origin && event.origin == origin) {
    onMessageReceived(event);
  }
}, false);

onMessageReceivedHandler = function (msg) {
  if (msg.cmd) {
    if (msg.cmd == "blurControls" && typeof msg.enabled == "boolean") {
      return blurControls(msg.enabled);
    } else if (msg.cmd == "setVideoOnClick") {
      return setVideoOnClick();
    }
  }
  return false;
}

// -------------------
// Parent window APIs
// -------------------

function getTranslation(string) {
  return postMessage(window.parent, { cmd: "translate", value: string});
}

function isOperatorMode() {
  return postMessage(window.parent, { cmd: "isOperatorMode" });
}

function getRoomName() {
  return postMessage(window.parent, { cmd: "getRoomName" });
}

function leaveRoom() {
  return postMessage(window.parent, { cmd: "leaveRoom" });
}

var lastSwitchTimeout = null;
var useTabletCamera = false;

function switchCamera() {
  useTabletCamera = !useTabletCamera;
  // Update
  var command;
  var hint;
  if (useTabletCamera) {
    command = "useTabletCamera";
    hint = "View from Pepper's Tablet camera";
  } else {
    command = "useHeadCamera";
    hint = "View from Pepper's Head camera";
  }

  var hintDiv = document.getElementById("camera-hint");
  if (hintDiv) {
    clearTimeout(lastSwitchTimeout)
    hintDiv.innerText = hint;
    hintDiv.classList.remove("fadeout");
    lastSwitchTimeout = setTimeout(() => {
      hintDiv.classList.add("fadeout");
    }, 500)
  }
  return postMessage(window.parent, { cmd: command });
}

function turn(angle) {
  return postMessage(window.parent, { cmd: "turn", angle: angle });
}

function moveForward() {
  return postMessage(window.parent, { cmd: "moveForward" });
}

function strafe(x, y) {
  return postMessage(window.parent, { cmd: "strafe", x: x, y: y });
}

function lookAt(x, y) {
  if (useTabletCamera) {
    return postMessage(window.parent, { cmd: "tabletLookAt", x: x, y: y });
  } else {
    return postMessage(window.parent, { cmd: "headLookAt", x: x, y: y });
  }
}

function getTwilioToken() {
  return postMessage(window.parent, { cmd: "getTwilioToken" });
}


// -------------------
// Child window APIs
// -------------------

function blurControls(enabled) {
  if (enabled) {
    var blurButtons = document.getElementsByClassName("robot-interaction");
    var i;
    for (i = 0; i < blurButtons.length; i++) {
      blurButtons[i].style.filter = "blur(3px)";
    }
  } else {
    //Unblur the buttons
    var blurButtons = document.getElementsByClassName("robot-interaction");
    var i;
    for (i = 0; i < blurButtons.length; i++) {
      blurButtons[i].style.filter = "none";
    }
  }
}

function setVideoOnClick() {
  document.querySelector('#remote-media').onclick = function (e) {
    var rect = e.target.getBoundingClientRect();
    var x = (e.clientX - rect.left) / (rect.width / 2) - 1; //x position within the element.
    var y = -((e.clientY - rect.top) / (rect.height / 2) - 1);  //y position within the element.
    lookAt(x, y);
  }
}

// -----
// Main
// -----

async function setTranslations() {
  $("#lookat-help").text(await getTranslation('lookat-help'));
}

async function setupGUI(roomname) {
  // Set room name text
  //document.getElementById('room-name').innerHTML = await getTranslation("room") + ": " + roomname;

  // Bind button to leave Room.
  document.getElementById('button-leave').onclick = function () {
    console.log('Leaving room...');
    leaveRoom();
  };

  document.getElementById('button-switch-camera').onclick = function () {
    switchCamera();
  }

  if (await isOperatorMode())
    setupOperatorControls();
}


function setupOperatorControls() {


  document.getElementById('local-media').style.display = "none";
  document.getElementById('button-switch-camera').style.display = "block";
  document.getElementById('controls').style.display = "block";
  document.getElementById('camera-controls').style.display = "flex";
  document.getElementById('camera-hint').style.display = "block";


  // Helpers for binding buttons

  function bindTurnButton(buttonId, angle) {
    var button = document.getElementById(buttonId);
    button.onmousedown = function () {
      console.log("mousedown " + buttonId);
      turn(angle)
      button.style.filter = "brightness(0.95)";
      setTimeout(() => {
        button.style.filter = "none"
      }, 100);
      return false;
    };
  }

  var intervals = {}

  function bindContinuousButton(buttonId, commandCallback) {
    var button = document.getElementById(buttonId);

    button.onmousedown = function () {
      console.log("mousedown start " + buttonId);
      commandCallback();
      button.style.filter = "brightness(0.9)";
      intervals[buttonId] = setInterval(commandCallback, 500);
      return false;
    };

    button.onmouseup = function () {
      console.log("mouseup " + buttonId);
      button.style.filter = "none"
      clearInterval(intervals[buttonId]);
    };
    button.onmouseout = function () {
      console.log("onmouseout"  + buttonId);
      button.style.filter = "none"
      clearInterval(intervals[buttonId]);
    };
  }

  // Turn Controls
  bindTurnButton('left90', 90)
  bindTurnButton('left45', 45)
  bindTurnButton('right45', -45)
  bindTurnButton('right90', -90)

  // Move controls
  var STRAFE_DISTANCE = 1;
  bindContinuousButton('forward', moveForward);
  bindContinuousButton('backward', () => strafe(0, -STRAFE_DISTANCE));
  bindContinuousButton('strafeleft', () => strafe(-STRAFE_DISTANCE, 0));
  bindContinuousButton('straferight', () => strafe(STRAFE_DISTANCE, 0));

  // General cleanup
  window.addEventListener('mouseup', function (e) {
    console.log("window mouseup");
    Object.values(intervals).forEach((interval) => {
      clearInterval(interval)
    });
  }, false);
}


$(document).ready(async function() {
  let roomname = await getRoomName();
  let twiliotoken = await getTwilioToken();
  setTranslations();
  setupGUI(roomname);
});
