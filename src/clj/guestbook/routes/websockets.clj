(ns guestbook.routes.websockets
  (:require [clojure.tools.logging :as log]
            [guestbook.messages :as msg]
            [guestbook.middleware :as middleware]
            [cljs.core :as c]
            [mount.core :refer [defstate]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]))

;; Create websocket app state
(defstate socket
  ;; Initialize Sente ws connection on app start
  :start (sente/make-channel-socket!
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

;; When the given message doesn't match any events,
;; we should omit error response.
(defmethod handle-message :default
  [{:keys [id]}]
  (log/debug "Received unrecognized websocket event type: " id)
  {:error (str "Unrecognized websocket event type: " (pr-str id))
   :id    id})

(defmethod handle-message :message/create!
  ;; event-message has useful keys
  ;; ?data - data sent to server 
  ;;         (? prefix indicates that it might not exist)
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
      (do
        (log/debug "Failed to save message: " ?data)
        response)
      (do
        (doseq [uid (:any @(:connected-uids socket))]
          (send! uid [:message/add response]))
        {:success true}))))

(defn receive-message! [{:keys [id ?reply-fn] :as message}]
  (log/debug "Got message with id: " id)
  (let [reply-fn (or ?reply-fn (fn [_]))]
    (when-some [response (handle-message message)]
      (reply-fn response))))

;; Setup message router, which handles incoming messages
;; and pass to handler function.
(defstate channel-router
  :start (sente/start-chsk-router!
        ;; `defstate` will automatically wait for 
        ;; `socket` state to be initialized
          (:ch-recv socket)
          #'receive-message!)
  :stop (when-let [stop-fn channel-router]
          (stop-fn)))

(defn websocket-routes []
  ["/ws"
   {;; Sente works well with ring middlewares,
    ;; so we can add them here.
    :middleware [middleware/wrap-csrf
                 middleware/wrap-formats]
    ;; If the client doesn't support websocket,
    ;; Sente will automatically use AJAX post/get.
    :get (:ajax-get-or-ws-handshake-fn socket)
    :post (:ajax-post-fn socket)}])

;; Channel list should be in singleton SET data structure
(defonce channels (atom #{}))

(defn connect! [channel]
  (log/info "Channel opened")
  (swap! channels conj channel))

(defn disconnect! [channel status]
  (log/info "Channel closed: " status)
  (swap! channels disj channel))



