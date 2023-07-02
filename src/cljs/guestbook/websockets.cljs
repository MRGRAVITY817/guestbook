(ns guestbook.websockets
  (:require [cljs.reader :as edn]))

(defonce channel (atom nil))

(defn connect!
  "Connect to given websocket url"
  [url receive-handler]
  (if-let [chan (js/WebSocket. url)]
    (do
      (.log js/console "Websocket connected!")
      (set! (.-onmessage chan)
            #(->> %
                  .-data
                  edn/read-string
                  receive-handler))
      ;; Set client's channel to connected channel.
      (reset! channel chan))
    ;; If failed to connect, throw an error.
    (throw (ex-info
            "Websocket connection failed..."
            {:url url}))))

(defn send-message! [msg]
  (if-let [chan @channel]
    ;; Success
    (.send chan (pr-str msg))
    ;; Fail
    (throw (ex-info
            "Could not send message, channel isn't open."
            {:message msg}))))

