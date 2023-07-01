(ns ^:dev/once guestbook.app
  (:require
   [devtools.core :as devtools]
   [guestbook.core :as core]))

(enable-console-print!)

(println "Loading evn/dev/cljs/guestbook/app.cljs...")

(devtools/install!)

(core/init!)

