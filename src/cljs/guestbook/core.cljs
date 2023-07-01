(ns guestbook.core
  (:require
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

;;
;; MESSAGES Layers
;;
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

;;
;; FORM Layers
;;
(rf/reg-event-db
 :form/set-field
 ;; Interceptors are simillar to middlewares
 ;; It transforms the input and outputs of our event handlers.
 ;; rf/path makes :form/fields to be entire db.
 [(rf/path :form/fields)]
 (fn [fields [_ id value]]
   (assoc fields id value)))

(rf/reg-event-db
 :form/clear-fields
 [((rf/path :form/fields))]
 (fn [_ _] {}))

(rf/reg-sub
 :form/fields
 (fn [db _]
   (:form/fields db)))

(rf/reg-sub
 :form/field
;; This :<- indicates that this is a 'derived subscription' 
;; from :form/fields subscription.
 :<- [:form/fields]
 (fn [fields [_ id]]
   (get fields id)))

(rf/reg-event-db
 :form/set-server-errors
 [(rf/path :form/server-errors)]
 (fn [_ [_ errors]]
   errors))

(rf/reg-sub
 :form/server-errors
 (fn [db _]
   (:form/server-errors db)))

(rf/reg-sub
 :form/validation-errors
 :<- [:from/fields]
 (fn [fields _]
   (validate-message fields)))

(rf/reg-sub
 :form/validation-errors?
 :<- [:form/validation-errors]
 (fn [errors _]
   (seq errors)))

(rf/reg-sub
 :form/errors
 :<- [:form/validation-errors]
 :<- [:form/server-errors]
 (fn [[validation server] _]
   (merge validation server)))

(rf/reg-sub
 :form/error
 :<- [:form/errors]
 (fn [errors [_ id]]
   (get errors id)))

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

(rf/reg-event-fx
 :message/send!
 (fn [{:keys [db]} [fields]]
   (POST "/api/message"
     {:format :json
      :headers {"Accept" "application/transit+json"
                "x-csrf-token" (.-value (.getElementById js/document "token"))}
      :params fields
      :handler #(rf/dispatch
                 [:messages/add
                  (-> fields (assoc :timestamp (js/Date.)))])
      :error-handler #(rf/dispatch
                       [:form/set-server-errors
                        (get-in % [:response :errors])])})
   {:db (dissoc db :form/server-errors)}))

(defn message-list [messages]
  [:ul.messages
   (for [{:keys [timestamp message name]} @messages]
     ^{:key timestamp}
     [:li
      [:time (.toLocaleString timestamp)]
      [:p message]
      [:p " - " name]])])

(defn errors-component [id]
  (when-let [error @(rf/subscribe [:form/error id])]
    [:div.notification.is-danger (string/join error)]))

(defn message-form []
  [:div
   [errors-component :server-error]
       ;; Name Field
   [:div.field
    [:label.label {:for :name} "Name"]
    [errors-component :name]
    [:input.input
     {:type :text
      :name :name
      :value @(rf/subscribe [:form/field :name])
      :on-change #(rf/dispatch
                   [:form/set-field
                    :name
                    (.. % -target -value)])}]]
       ;; Message Field
   [:div.field
    [:label.label {:for :message} "Message"]
    [errors-component :message]
    [:textarea.textarea
     {:name :message
      :value @(rf/subscribe [:form/field :message])
      :on-change #(rf/dispatch
                   [:form/set-field
                    :message
                    (.. % -target -value)])}]]
       ;; Submit Button
   [:input.button.is-primary
    {:type :submit
     :disabled @(rf/subscribe [:form/validation-errors?])
     :on-click #(rf/dispatch [:message/send!
                              @(rf/subscribe [:form/fields])])
     :value "comment"}]])

(defn home []
  (let [messages (rf/subscribe [:messages/list])] ;; Local state
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

(defn ^:dev/after-load mount-components []
  (rf/clear-subscription-cache!)
  (.log js/console "Mounting Components...")
  (dom/render [#'home] (.getElementById js/document "content"))
  (.log js/console "Components Mounted!"))

(defn init! []
  (.log js/console "Initializing App...")
  (rf/dispatch [:app/initialize]) ;; Put event in queue
  (get-messages)
  (mount-components))

(dom/render
 [home]
 (.getElementById js/document "content"))
