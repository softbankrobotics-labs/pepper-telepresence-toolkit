
// Messaging system across iframes, based on postMessage, allowing to
// send message and receive associated answers.

var pendingRequestPromises = {};
var onMessageReceivedHandler;

function createRandomString() {
  let cryptoObj = window.crypto || window.msCrypto;
  let randomValueArray = new Uint32Array(1);
  cryptoObj.getRandomValues(randomValueArray);
  return randomValueArray[0].toString(36).substring(1);
}

function postMessage(targetWindow, message) {
  let trackingId = createRandomString();
  let msg = {
    trackingId: trackingId,
    body: message
  }
  //console.log(`Posting message:`, JSON.stringify(msg, null, '  '));
  targetWindow.postMessage(msg, "*");
  let deferred = $.Deferred();
  pendingRequestPromises[trackingId] = deferred;
  return deferred.promise();
}

function sendResponse(targetWindow, message, trackingId) {
  let msg = {
    trackingId: trackingId,
    body: message
  }
  //console.log(`Sending response:`, JSON.stringify(msg, null, '  '));
  targetWindow.postMessage(msg, "*");
}

function onMessageReceived(event) {
  //console.log(`Received message:`, JSON.stringify(event.data, null, '  '));
  let sendingWindow = event.source;
  let msg = event.data;
  let trackingId;
  try {
    trackingId = msg['trackingId'];
  } catch (e) {
    console.warn('No tracking id found in message: ', JSON.stringify(msg, null, '  '));
    return;
  }
  let body;
  try {
    body = msg['body'];
  } catch (e) {
    console.warn('No body found in message: ', JSON.stringify(msg, null, '  '));
    return;
  }
  let deferred;
  if (trackingId) {
    deferred = pendingRequestPromises[trackingId];
  }

  if (!deferred) {
    if (!onMessageReceivedHandler) {
      console.warn('onMessageReceivedHandler is not defined.');
      return;
    }
    let response = onMessageReceivedHandler(body);
    Promise.resolve(response).then(function (value) {
      sendResponse(sendingWindow, value, trackingId);
    });
  }
  else {
    deferred.resolve(body);
    delete pendingRequestPromises[trackingId];
  }
}
