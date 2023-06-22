(ns guestbook.routes.home
  (:require
   [guestbook.layout :as layout]
   [guestbook.db.core :as db]
   [clojure.java.io :as io]
   [guestbook.middleware :as middleware]
   [struct.core :as st]
   [ring.util.response]
   [ring.util.http-response :as response]))

(defn home-page [{:keys [flash] :as request}]
  (layout/render
   request
   "home.html"
   (merge
    {:messages (db/get-messages)}
    (select-keys flash [:name :message :errors]))))

(defn about-page [request]
  (layout/render request "about.html"))

;; form validation using `Struct` library (it's like zod in Node.js)
(def message-schema [[:name st/required st/string]
                     [:message st/required st/string
                      {:message "message must contain at least 10 characters"
                       :validate #(>= (count %) 10)}]])

(defn validate-message [params]
  ;; first element of st/validate returns nil when data is valid
  (first (st/validate params message-schema)))

(defn save-message! [{:keys [params]}]
  (if-let [errors (validate-message params)]
    ;; If form input is invalid, show error message via flash.
    (-> (response/found "/")
        (assoc :flash (assoc params :errors errors)))
    ;; Else, save the message in database.
    (do
      (db/save-message! params)
      (response/found "/"))))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/about" {:get about-page}]
   ["/message" {:post save-message!}]])

