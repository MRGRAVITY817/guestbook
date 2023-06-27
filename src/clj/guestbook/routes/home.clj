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
                       :validate (fn [msg] (>= (count msg) 10))}]])

(defn validate-message [params]
  ;; first element of st/validate returns nil when data is valid
  (first (st/validate params message-schema)))

(defn save-message! [{:keys [params]}]
  (if-let [errors (validate-message params)]
    (response/bad-request {:errors errors})
    (try
      (db/save-message! params)
      (response/ok {:status :ok})
      (catch Exception _e
        (response/internal-server-error
         {:errors {:server-error ["Failed to save message!"]}})))))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/about" {:get about-page}]
   ["/message" {:post save-message!}]])

