(ns guestbook.routes.services
  (:require
   [guestbook.messages :as msg]
   [guestbook.middleware :as middleware]
   [ring.util.http-response :as response]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]))

(defn service-routes []
  ["/api"
   {:middleware [middleware/wrap-formats]
    :swagger {:id ::api}}
   ;; Swagger
   ["" {:no-doc true}
    ["/swagger.json"
     {:get (swagger/create-swagger-handler)}]
    ["/swagger-ui*"
     {:get (swagger-ui/create-swagger-ui-handler
            {:url "/api/swagger.json"})}]]
   ;; API endpoints
   ["/messages"
    {:get (fn [_] (response/ok (msg/message-list)))}]
   ["/message"
    {:post
     (fn [{:keys [params]}]
       (try
         (msg/save-message! params)
         ;; Success
         (response/ok {:status :ok})
         ;; Failed
         (catch Exception e
           (let [{id     :guestbook/error-id
                  errors :errors} (ex-data e)]
             (case id
               :validation
               (response/bad-request {:errors errors})
               ;; else
               (response/internal-server-error
                {:errors {:server-error ["Failed to save message!"]}}))))))}]])







