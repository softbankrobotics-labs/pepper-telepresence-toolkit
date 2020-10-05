const functions = require('firebase-functions');

const { jwt: { AccessToken } } = require('twilio');

const VideoGrant = AccessToken.VideoGrant;
// Max. period that a Participant is allowed to be in a Room (currently 14400 seconds or 4 hours)
const MAX_ALLOWED_SESSION_DURATION = 14400;

exports.token = functions.https.onCall((data, context) => {

  if (!context.auth || !context.auth.uid) {
    return { message: 'Authentication Required', code: 401 };
  }

  // Create an access token which we will sign and return to the client,
  // containing the grant we just created.
  const token = new AccessToken(
    functions.config().twilio.account_sid,
    functions.config().twilio.api_key,
    functions.config().twilio.api_secret,
    { ttl: MAX_ALLOWED_SESSION_DURATION }
  );

  // Assign the generated identity to the token.
  token.identity = data.identity;

  // Grant the access token Twilio Video capabilities.
  const grant = new VideoGrant();
  token.addGrant(grant);

  return {
    "token": token.toJwt()
  }
});
