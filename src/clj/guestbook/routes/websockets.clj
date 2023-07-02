(ns guestbook.routes.websockets
  (:require [clojure.tools.logging :as log]
            [guestbook.messages :as msg]
            [cljs.core :as c]
            [mount.core :refer [defstate]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]))

;; Create websocket app state
(defstate socket
  ;; Initialize Sente ws connection on app start
  :start (sente/make-channel-socket-client!
          ;; with http-kit server adapter
          (get-sch-adapter)
          ;; Sente will automatically allocate unique id for 
          ;; each channels with :uid keyword.
          ;; However we don't wanna use default :uid for id
          ;; since that's for ring session, hence we alter user-id 
          ;; with ring-req.params.client-id, which is uuid and 
          ;; applicable to http-kit.
          {:user-id-fn #(get-in % [:params :client-id])}))

(defn send!
  "Send message to user via websocket"
  [uid message]
  (println "Sending message: " message)
  (let [send-msg (:send-fn socket)]
    (send-msg uid message)))

(defmulti handle-message (fn [{:keys [id]}] id))

(defmethod handle-message :default
  [{:keys [id]}]
  (log/debug "Received unrecognized websocket event type: " id))

(defmethod handle-message :message/create!
  ;; event-message has useful keys
  ;; ?data - data sent to server
  ;; uid   - A user id 
  [{:keys [?data uid] :as message}]
  (let [response (try
                   (msg/save-message! ?data)
                   (assoc ?data :timestamp (java.util.Date.))
                   (catch Exception e
                     (let [{id     :guestbook/error-id
                            errors :errors} (ex-data e)]
                       (case id
                         :validation
                         {:errors errors}
                         ;; else
                         {:errors
                          {:server-error ["Failed to save message!"]}}))))]
    (if (:errors response)
      (send! uid [:message/creation-errors response])
      (doseq [uid (:any @(:connected-uids socket))]
        (send! uid [:message/add response])))))

(defn receive-message! [{:keys [id] :as message}]
  (log/debug "Got message with id: " id)
  (handle-message message))

;; Channel list should be in singleton SET data structure
(defonce channels (atom #{}))

(defn connect! [channel]
  (log/info "Channel opened")
  (swap! channels conj channel))

(defn disconnect! [channel status]
  (log/info "Channel closed: " status)
  (swap! channels disj channel))

(defn handler [request]
  (http-kit/with-channel request channel
    (connect! channel)
    (http-kit/on-close channel (partial disconnect! channel))
    (http-kit/on-receive channel (partial handle-message! channel))))

(defn websocket-routes []
  ["/ws"
   {:get handler}])


