(ns guestbook.validation
  (:require
   [struct.core :as st]))

;; form validation using `Struct` library (it's like zod in Node.js)
(def message-schema
  [[:name st/required st/string]
   [:message st/required st/string
    {:message "message must contain at least 10 characters"
     :validate (fn [msg] (>= (count msg) 10))}]])

(defn validate-message [params]
  ;; first element of st/validate returns nil when data is valid
  (first (st/validate params message-schema)))
