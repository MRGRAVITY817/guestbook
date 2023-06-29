(ns guestbook.handler
  (:require
   [guestbook.env :refer [defaults]]
   [guestbook.middleware :as middleware]
   [guestbook.layout :refer [error-page]]
   [guestbook.routes.home :refer [home-routes]]
   [guestbook.routes.services :refer [service-routes]]
   [reitit.ring :as ring]
   [reitit.ring.middleware.dev :as dev]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [ring.middleware.webjars :refer [wrap-webjars]]
   [mount.core :as mount]))

(mount/defstate init-app
  :start ((or (:init defaults) (fn [])))
  :stop  ((or (:stop defaults) (fn []))))

(mount/defstate app-routes
  :start
  (ring/ring-handler

   (ring/router
    [(home-routes)
     (service-routes)]
      ;;Debug reitit middlewares (printed in console)
      ;;This will not show other middlewares
    {:reitit.middleware/transform dev/print-request-diffs})

   (ring/routes
    (ring/create-resource-handler
     {:path "/"})
    (wrap-content-type
     (wrap-webjars (constantly nil)))
    (ring/create-default-handler
     {:not-found
      (constantly (error-page {:status 404, :title "404 - Page not found"}))
      :method-not-allowed
      (constantly (error-page {:status 405, :title "405 - Not allowed"}))
      :not-acceptable
      (constantly (error-page {:status 406, :title "406 - Not acceptable"}))}))))

(defn app []
  (middleware/wrap-base #'app-routes))
