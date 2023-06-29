(ns guestbook.core
  (:require
   [reagent.core :as r]
   [reagent.dom :as dom]
   [re-frame.core :as rf]
   [ajax.core :refer [GET POST]]
   [clojure.string :as string]
   [guestbook.validation :refer [validate-message]]))

(defn get-messages [messages]
  (GET "/messages"
    {:headers {"Accept" "application/transit+json"}
     :handler #(reset! messages (:messages %))}))

(defn message-list [messages]
  (println messages)
  [:ul.messages
   (for [{:keys [timestamp message name]} @messages]
     ^{:key timestamp}
     [:li
      [:time (.toLocaleString timestamp)]
      [:p message]
      [:p " - " name]])])

(defn send-message!
  "Send form values to server (name and message)"
  [fields errors messages]
  (if-let [validation-errors (validate-message @fields)]
    (reset! errors validation-errors)
    (POST "/message"
      {:format :json
       :headers {;; Transit will auto convert Clojure data structures 
               ;; into HTML friendly string
                 "Accept" "application/transit+json"
                 "x-csrf-token" (.-value (.getElementById js/document "token"))}
       :params @fields
       :handler       (fn [_]
                        (swap! messages conj
                               (assoc @fields
                                      :timestamp (js/Date.)))
                        (reset! fields nil)
                        (reset! errors nil))
       :error-handler (fn [e]
                        (.log js/console (str e))
                        (reset! errors (-> e :response :errors)))})))

(defn errors-component [errors id]
  (when-let [error (id @errors)]
    [:div.notification.is-danger (string/join error)]))

(defn message-form [messages]
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
         :on-click #(send-message! fields errors messages)
         :value "comment"}]])))

;; Create Reframe event, which contains info about 
;; whether messages are being loaded or not.
(rf/reg-event-fx
 :app/initialize
 (fn [_ _]
   {:db {:messages/loading? true}}))

;; Create subscription so that other function components can
;; subscribe to :messages/loading? event.
(rf/reg-sub
 :messages/loading?
 (fn [db _]
   (:messages/loading? db)))

(defn home []
  (let [messages (r/atom nil)]
    (rf/dispatch [:app/initialize])
    (get-messages messages)
    (fn []
      (if @(rf/subscribe [:messages/loading?])
        [:div>div.row>div.span12>h3 "Loading Messages..."]
        [:div.content>div.columns.is-centered>div.column.is-two-thirds
         [:div.columns>div.column
          [:h3 "Messages"]
          [message-list messages]]
         [:div.columns>div.column
          [message-form messages]]]))))

(dom/render
 [home]
 (.getElementById js/document "content"))
