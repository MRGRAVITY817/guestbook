(ns guestbook.routes.services
  (:require
   [guestbook.auth :as auth]
   [guestbook.messages :as msg]
   [guestbook.middleware.formats :as formats]
   [ring.util.http-response :as response]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [reitit.coercion.spec :as spec-coercion]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.multipart :as multipart]
   [reitit.ring.middleware.parameters :as parameters]))

(declare login-service)

(defn service-routes []
  ["/api"
   {:middleware [;; query-params & form params
                 parameters/parameters-middleware
                 ;; content-negotiation
                 muuntaja/format-negotiate-middleware
                 ;; encoding response body
                 muuntaja/format-response-middleware
                 ;; exception handling
                 exception/exception-middleware
                 ;; decoding request body
                 muuntaja/format-request-middleware
                 ;; coercing response bodies
                 coercion/coerce-response-middleware
                 ;; coercing request parameters
                 coercion/coerce-request-middleware
                 ;; multipart pararms 
                 multipart/multipart-middleware]
    :muuntaja formats/instance
    :coercion spec-coercion/coercion
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
    {:get
     {:responses
      {200
       {:body ;; Data spec for response body
        {:messages
         [{:id        pos-int? ;; Predicates for swaggers
           :name      string?
           :message   string?
           :timestamp inst?}]}}}
      :handler
      (fn [_]
        (response/ok (msg/message-list)))}}]
   ["/message"
    {:post
     {:parameters
      {:body ;; Data spec for request body parameters
       {:name    string?
        :message string?}}

      :responses
      {200 {:body map?}
       400 {:body map?}
       500 {:errors map?}}

      :handler
      (fn [{{params :body} :parameters}]
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
                 {:errors {:server-error ["Failed to save message!"]}}))))))}}]
   ["/login" login-service]])

(def login-service
  {:post
   {:parameters
    {:body
     {:login string?
      :password string?}}
    :responses
    {200
     {:body {:identity {:login string? :created_at inst?}}}
     401
     {:body {:message string?}}}
    :handler
    (fn [{{{:keys [login password]} :body} :parameters
          session              :session}]
      (if-some [user (auth/authenticate-user login password)]
        ((-> response/ok {:identify user})
         (assoc :session (assoc session :identity user)))
        (response/unauthorized {:message "Incorrect login or password."})))}})





