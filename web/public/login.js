
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
  return false;
}

// -------------------
// Parent window APIs
// -------------------

function getTranslation(string) {
  return postMessage(window.parent, { cmd: "translate", value: string});
}

function joinRoom(username, roomname) {
  return postMessage(window.parent, { cmd: 'joinRoom', username: username, roomname: roomname });
}

function isFirebaseEnabled() {
  return postMessage(window.parent, { cmd: 'isFirebaseEnabled' });
}

function isTranslationAvailable() {
  return postMessage(window.parent, { cmd: 'isTranslationAvailable' });
}

function isOperatorMode() {
  return postMessage(window.parent, { cmd: "isOperatorMode" });
}

function firebaseSignIn(login, password, roomname) {
  return postMessage(window.parent, {
    cmd: "firebaseSignIn",
    login: login,
    password: password,
    roomname: roomname
  });
}

// -----
// Main
// -----

var useFirebase;
var usernameInput = document.getElementById('user-name-entry');
var roomInput = document.getElementById('room-name-entry');
var defaultUsername = "driver"; // Used in operator mode

function isString (value) {
  return typeof value === 'string' || value instanceof String;
}

var joinButton = document.getElementById('join-btn');
joinButton.onmousedown = function () {
  joinButton.src = "img/join_pressed.png"
}

joinButton.onmouseup = function () {
  joinButton.src = "img/join.png"
}

joinButton.onclick = async function () {
  if (await isOperatorMode()) {
    usernameInput.value = defaultUsername;
  } else {
    usernameInput = document.getElementById('user-name-entry');
  }
  roomInput = document.getElementById('room-name-entry');

  if (roomInput.value != '') {
    console.log(roomInput.value.length);
    if (roomInput.value.length >= 20) {
      if (useFirebase) {
        var loginInput = document.getElementById('login-entry');
        var passwordInput = document.getElementById('password-entry');
        firebaseSignIn(loginInput.value, passwordInput.value, roomInput.value).then(function (result) {
          if (isString(result)) {
            document.getElementById('room-instructions').innerHTML = result;
          }
        });
      } else {
        if (usernameInput.value != '') {
          joinRoom(usernameInput.value, roomInput.value);
        } else {
          document.getElementById('room-instructions').innerHTML
            = await getTranslation('enter-username')
        }
      }
    } else {
      document.getElementById('room-instructions').innerHTML
        = await getTranslation('room-name-length');
    }
  } else {
    if (await isOperatorMode()) {
      document.getElementById('room-instructions').innerHTML
        = await getTranslation('enter-room-name-operator');
    } else {
      document.getElementById('room-instructions').innerHTML
        = await getTranslation('enter-room-name');
    }
  }
}

async function setTranslations() {
  $("input#user-name-entry").prop({
    placeholder: await getTranslation('username-placeholder')
  });
  $("input#login-entry").prop({
    placeholder: await getTranslation('login-placeholder')
  });
  $("input#password-entry").prop({
    placeholder: await getTranslation('password-placeholder')
  });
  $("input#room-name-entry").prop({
    placeholder: await getTranslation('room-placeholder')
  });
  $("#welcome-text p").text(await getTranslation('welcome'));
}

async function onReady() {
  setTranslations();
  useFirebase = await isFirebaseEnabled();

  if (useFirebase) {
    document.getElementById('login-entry').style.display = "block";
    document.getElementById('password-entry').style.display = "block";
  } else {
    //Set default values for the inputs if operator mode
    if (!await isOperatorMode()) {
      document.getElementById('user-name-entry').style.display = "block";
    }
  }
}

$(document).ready(async function() {

  // Wait for translations to be loaded by parent javascript, then start.
  let intervalId = setInterval(async () => {
    if (await isTranslationAvailable()) {
      clearInterval(intervalId);
      onReady();
    }
  }, 50);
});
