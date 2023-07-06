(ns guestbook.core
  (:require
   [reagent.core :as r]
   [reagent.dom :as dom]
   [re-frame.core :as rf]
   [ajax.core :refer [GET]]
   [clojure.string :as string]
   [guestbook.validation :refer [validate-message]]
   [mount.core :as mount]))

;; Register Reframe event, which contains info about 
;; whether messages are being loaded or not.
(rf/reg-event-fx
 :app/initialize
 (fn [_ _]
   {:db {:message/loading? true}
    :dispatch [:messages/load]}))

;;
;; MESSAGES Layers
;;
(rf/reg-fx
 :ajax/get
 (fn [{:keys [url success-event error-event success-path]}]
   (GET url
     (cond-> {:headers {"Accept" "application/transit+json"}}
       success-event
       (assoc
        :handler #(rf/dispatch
                   (conj success-event
                         (if success-path
                           (get-in % success-path)
                           %))))
       error-event
       (assoc
        :error-handler #(rf/dispatch (conj error-event %)))))))

;; Get messages
(rf/reg-event-fx
 :messages/load
 (fn [{:keys [db]} _]
   {:db (assoc db :message/loading? true)
    :ajax/get {:url "/api/messages"
               :success-path [:messages]
               :success-event [:message/set]}}))

;; Register subscription so that other function components can
;; subscribe to :message/loading? event.
(rf/reg-sub
 :message/loading?
 (fn [db _]
   (:message/loading? db)))

;; Use reg-event-db for events that only use db effect.
(rf/reg-event-db
 :message/set
 (fn [db [_ messages]]
   (-> db (assoc :message/loading? false
                 :message/list messages))))

(rf/reg-sub
 :message/list
 (fn [db _]
   (:message/list db [])))

;; Send message event
(rf/reg-event-db
 :message/add
 (fn [db [_ message]]
   ;; Return db map where :message/list has been 
   ;; appended with new message at front (conjoined) .
   (update db :message/list conj message)))

(rf/reg-event-fx
 :message/send!-called-back
 (fn [_ [_ {:keys [success errors]}]]
   (if success
     {:dispatch [:form/clear-fields]}
     {:dispatch [:form/set-server-errors errors]})))

;; Rather than calling the function directly,
;; it's better to just return a map that contains enough
;; info how to handle submission.
(rf/reg-event-fx
 :message/send!
 (fn [{:keys [db]} [_ fields]]
   {:db (dissoc db :form/server-errors)
    :ws/send! {:message [:message/create! fields]
               :timeout 10000
               :callback-event [:message/send!-called-back]}}))

(defn message-list [messages]
  [:ul.messages
   (for [{:keys [timestamp message name]} @messages]
     ^{:key timestamp}
     [:li
      [:time (.toLocaleString timestamp)]
      [:p message]
      [:p " - " name]])])

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
 [(rf/path :form/fields)]
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
 :<- [:form/fields]
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

(defn errors-component [id]
  (when-let [error @(rf/subscribe [:form/error id])]
    [:div.notification.is-danger (string/join error)]))

(defn text-input [{val :value
                   attrs :attrs
                   :keys [on-save]}]
  (let [draft (r/atom nil)
        value (r/track #(or @draft @val ""))]
    (fn []
      [:input.input
       (merge attrs {:type       :text
                     :value      @value
                     :on-change  #(reset! draft (.. % -target -value))
                     :on-focus   #(reset! draft (or @val ""))
                     :on-blur    (fn []
                                   (on-save (or @draft ""))
                                   (reset! draft nil))})])))

(defn textarea-input [{val   :value
                       attrs :attrs
                       :keys [on-save]}]
  (let [draft (r/atom nil)
        value (r/track #(or @draft @val ""))]
    (fn []
      [:textarea.textarea
       (merge attrs {:value      @value
                     :on-change  #(reset! draft (.. % -target -value))
                     :on-focus   #(reset! draft (or @val ""))
                     :on-blur    (fn []
                                   (on-save (or @draft ""))
                                   (reset! draft nil))})])))

(defn message-form []
  [:div
   [errors-component :server-error]

   [:div.field
    [:label.label {:for :name} "Name"]
    [errors-component :name]
    [text-input {:attrs {:name name}
                 :value (rf/subscribe [:form/field :name])
                 :on-save #(rf/dispatch [:form/set-field :name %])}]]

   [:div.field
    [:label.label {:for :message} "Message"]
    [errors-component :message]
    [textarea-input {:attrs {:name :message}
                     :value (rf/subscribe [:form/field :message])
                     :on-save #(rf/dispatch [:form/set-field :message %])}]]

   [:input.button.is-primary
    {:type :submit
     :disabled @(rf/subscribe [:form/validation-errors?])
     :on-click #(rf/dispatch [:message/send!
                              @(rf/subscribe [:form/fields])])
     :value "comment"}]])

(defn reload-messages-button []
  (let [loading? (rf/subscribe [:message/loading?])]
    [:button.button.is-info.is-fullwidth
     {:on-click #(rf/dispatch [:messages/load])
      :disabled @loading?}
     (if @loading?
       "Loading Messages"
       "Refresh Messages")]))

(defn home []
  (let [messages (rf/subscribe [:message/list])] ;; Local state
    (fn []
      [:div.content>div.columns.is-centered>div.column.is-two-thirds
       (if @(rf/subscribe [:message/loading?])
         [:h3 "Loading Messages..."]
         [:div
          [:div.columns>div.column
           [:h3 "Messages"]
           [message-list messages]]
          [:div.columns>div.column
           [reload-messages-button]]
          [:div.columns>div.column
           [message-form]]])])))

(defn ^:dev/after-load mount-components []
  (rf/clear-subscription-cache!)
  (.log js/console "Mounting Components...")
  (dom/render [#'home] (.getElementById js/document "content"))
  (.log js/console "Components Mounted!"))

(defn init! []
  (.log js/console "Initializing App...")
  (mount/start)
  (rf/dispatch [:app/initialize])
  (mount-components))

(dom/render
 [home]
 (.getElementById js/document "content"))

