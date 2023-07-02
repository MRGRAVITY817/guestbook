(ns guestbook.websockets
  (:require-macros [mount.core :refer [defstate]])
  (:require [re-frame.core :as rf]
            [taoensso.sente :as sente]
            mount.core))

(defonce channel (atom nil))

(defstate socket
  :start (sente/make-channel-socket!
          "/ws"
          (.-value (.getElementById js/document "token"))
          {:type           :auto ;; automatically choose betweeen ajax/ws
           :wrap-recv-evs? false})) ;; get structured client event.

(defn send! [message]
  (if-let [send-fn (:send-fn @socket)]
    (send-fn message)
    (throw (ex-info
            "Couldn't send message, channel isn't open!"
            {:message message}))))

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

