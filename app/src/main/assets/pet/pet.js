(function () {
  var stage = document.getElementById("stage");
  var pet = document.getElementById("pet");
  var emotion = "CALM";
  var speaking = false;
  var skin = "pink";

  var EMOTIONS = ["CALM", "WARM", "PLAYFUL", "CONCERNED"];

  function emotionFile(name) {
    return "skins/" + skin + "/" + String(name).toLowerCase() + ".svg";
  }

  function applyVisual() {
    var key = EMOTIONS.indexOf(emotion) >= 0 ? emotion : "CALM";
    var src = emotionFile(key);
    if (pet.getAttribute("src") !== src) {
      pet.setAttribute("src", src);
    }
    if (speaking) {
      stage.classList.add("speaking");
    } else {
      stage.classList.remove("speaking");
    }
  }

  window.setSkin = function (value) {
    if (!value) return;
    var next = String(value).toLowerCase();
    if (next === skin) return;
    skin = next;
    applyVisual();
  };

  window.setEmotion = function (value) {
    if (!value) return;
    emotion = String(value).toUpperCase();
    applyVisual();
  };

  window.setSpeaking = function (value) {
    speaking = !!value && value !== "false" && value !== "0";
    applyVisual();
  };

  window.showBubble = function () {
    /* Bubble is rendered natively in Compose beside the WebView. */
  };

  stage.addEventListener("click", function () {
    stage.classList.add("tap");
    setTimeout(function () {
      stage.classList.remove("tap");
    }, 140);
    try {
      if (window.PetBridge && window.PetBridge.onPetClick) {
        window.PetBridge.onPetClick();
      }
    } catch (e) {
      /* ignore */
    }
  });

  applyVisual();
})();
