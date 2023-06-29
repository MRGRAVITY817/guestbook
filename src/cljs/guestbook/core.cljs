(ns guestbook.core
  (:require
   [reagent.core :as r]
   [reagent.dom :as dom]
   [re-frame.core :as rf]
   [ajax.core :refer [GET POST]]
   [clojure.string :as string]
   [guestbook.validation :refer [validate-message]]))

;; Register Reframe event, which contains info about 
;; whether messages are being loaded or not.
(rf/reg-event-fx
 :app/initialize
 (fn [_ _]
   {:db {:messages/loading? true}}))

;; Register subscription so that other function components can
;; subscribe to :messages/loading? event.
(rf/reg-sub
 :messages/loading?
 (fn [db _]
   (:messages/loading? db)))

;; Use reg-event-db for events that only use db effect.
(rf/reg-event-db
 :messages/set
 (fn [db [_ messages]]
   (-> db (assoc :messages/loading? false
                 :messages/list messages))))

;; Get messages

(rf/reg-sub
 :messages/list
 (fn [db _]
   (:messages/list db [])))

(defn get-messages []
  (GET "/api/messages"
    {:headers {"Accept" "application/transit+json"}
     :handler #(rf/dispatch [:messages/set (:messages %)])}))

;; Send message event
(rf/reg-event-db
 :messages/add
 (fn [db [_ message]]
   ;; Return db map where :messages/list has been 
   ;; appended with new message at front (conjoined) .
   (update db :messages/list conj message)))

;; Actual function to send message
(defn send-message!
  "Send form values to server (name and message)"
  [fields errors]
  (if-let [validation-errors (validate-message @fields)]
    (reset! errors validation-errors)
    (POST "/api/message"
      {:format :json
       :headers {;; Transit will auto convert Clojure data structures 
                 ;; into HTML friendly string
                 "Accept" "application/transit+json"
                 "x-csrf-token" (.-value (.getElementById js/document "token"))}
       :params @fields
       :handler (fn [_]
                  (rf/dispatch
                   [:messages/add (assoc @fields :timestamp (js/Date.))])
                  (reset! fields nil)
                  (reset! errors nil))
       :error-handler (fn [e]
                        (.log js/console (str e))
                        (reset! errors (-> e :response :errors)))})))

(defn message-list [messages]
  [:ul.messages
   (for [{:keys [timestamp message name]} @messages]
     ^{:key timestamp}
     [:li
      [:time (.toLocaleString timestamp)]
      [:p message]
      [:p " - " name]])])

(defn errors-component [errors id]
  (when-let [error (id @errors)]
    [:div.notification.is-danger (string/join error)]))

(defn message-form []
  (let [fields (r/atom {})
        errors (r/atom nil)]
    (fn []
      [:div
       [errors-component errors :server-error]
       ;; Name Field
       [:div.field
        [:label.label {:for :name} "Name"]
        [errors-component errors :name]
        [:input.input
         {:type :text
          :name :name
          :on-change #(swap! fields
                             assoc :name (-> % .-target .-value))
          :value (:name @fields)}]]
       ;; Message Field
       [:div.field
        [:label.label {:for :message} "Message"]
        [errors-component errors :message]
        [:textarea.textarea
         {:name :message
          :value (:message @fields)
          :on-change #(swap! fields
                             assoc :message (-> % .-target .-value))}]]
       ;; Submit Button
       [:input.button.is-primary
        {:type :submit
         :on-click #(send-message! fields errors)
         :value "comment"}]])))

(defn home []
  (let [messages (rf/subscribe [:messages/list])] ;; Local state
    (rf/dispatch [:app/initialize]) ;; Put event in queue
    (get-messages)
    (fn []
      [:div.content>div.columns.is-centered>div.column.is-two-thirds
       (if @(rf/subscribe [:messages/loading?])
         [:h3 "Loading Messages..."]
         [:div
          [:div.columns>div.column
           [:h3 "Messages"]
           [message-list messages]]
          [:div.columns>div.column
           [message-form]]])])))

(dom/render
 [home]
 (.getElementById js/document "content"))
