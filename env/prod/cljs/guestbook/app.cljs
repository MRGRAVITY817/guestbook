(ns guestbook.app
  (:require
   [guestbook.core :as core]))

;; Ignore any println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
