function getUrlParameter(parameter) {
  var url = new URL(window.location.href);
  return url.searchParams.get(parameter);
}

function loadTranslations() {
  return $.i18n().load( {
    en: '/i18n/en.json',
    fr: '/i18n/fr.json'
  }).done(function() {
    var scriptElement = document.createElement('script');
    var lang = getUrlParameter("lang");
    if (lang == 'fr') {
      console.log("language: French");
      $.i18n().locale = 'fr';
      scriptElement.src = "https://cdnjs.cloudflare.com/ajax/libs/jquery-validate/1.19.2/localization/messages_fr.min.js";
      scriptElement.integrity = "sha512-O4IbKhkgn9LcZRHg1cAVXj2LEA4ywCsw6UOCa3gQmnCz7bHer98U5/IDBgHc9mlISEfuMeP/F6FrQ0ILjnj3Bg==";
      scriptElement.crossorigin="anonymous"
    }
    else {
      console.log("language: English");
      $.i18n().locale = 'en';
    }
    document.body.appendChild(scriptElement);
  });
}

function getTranslation(message) {
  return $.i18n(message);
}

function applyTranslations() {
    $("body").i18n();
    $("button#button-tts1").prop({
      value: getTranslation('tts-sentence1-value')
    });
    $("button#button-tts2").prop({
      value: getTranslation('tts-sentence2-value')
    });
    $("button#button-tts3").prop({
      value: getTranslation('tts-sentence3-value')
    });
    $("input#user-name-entry").prop({
      placeholder: getTranslation('username-placeholder')
    });
    $("input#login-entry").prop({
      placeholder: getTranslation('login-placeholder')
    });
    $("input#password-entry").prop({
      placeholder: getTranslation('password-placeholder')
    });
    $("input#room-name-entry").prop({
      placeholder: getTranslation('room-placeholder')
    });
}
