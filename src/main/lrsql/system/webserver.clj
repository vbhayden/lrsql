(ns lrsql.system.webserver
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :refer [input-stream]]
            [jdk.security.KeyStore :as ks]
            [jdk.security.Key :as k]
            [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]
            [com.yetanalytics.lrs.pedestal.routes :refer [build]]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [lrsql.admin.routes :refer [add-admin-routes]]
            [lrsql.spec.config :as cs]
            [lrsql.system.util :refer [assert-config]]))

(defn- file->keystore
  "Return a `java.security.KeyStore` instance from `filepath`, protected
   by the keystore `password`."
  [filepath password]
  (let [istream (input-stream filepath)
        pass    (char-array password)]
    (doto (ks/*get-instance (ks/*get-default-type))
      (ks/load istream pass))))

(defn- keystore->private-key
  "Return a string representation of the private key stored in `keystore`
   denoted by `alias` and protected by `password`."
  [keystore alias password]
  (-> keystore
      (ks/get-key alias (char-array password))
      k/get-encoded
      slurp))

(defn- service-map
  "Create a new service map for the webserver."
  [lrs config]
  (let [;; Destructure webserver config
        {:keys [http2?
                http-host
                http-port
                ssl-port
                key-file
                key-alias
                key-password]
         jwt-exp :jwt-expiration-time
         jwt-lwy :jwt-expiration-leeway}
        config
        ;; Keystore and private key
        ;; The private key is used as the JWT symmetric secret
        keystore    (file->keystore key-file key-password)
        private-key (keystore->private-key keystore key-alias key-password)
        ;; Make routes
        routes (->> (build {:lrs lrs})
                    (add-admin-routes {:lrs    lrs
                                       :exp    jwt-exp
                                       :leeway jwt-lwy
                                       :secret private-key}))]
    {:env                 :prod
     ::http/routes        routes
     ::http/resource-path "/public"
     ::http/type          :jetty
     ::http/host          http-host
     ::http/port          http-port
     ::http/join?         false
     ::http/allowed-origins
     {:creds           true
      :allowed-origins (constantly true)}
     ::http/container-options
     {:h2c?         true
      :h2?          http2?
      :ssl?         true
      :ssl-port     ssl-port
      :keystore     keystore
      :key-password key-password}}))

(defrecord Webserver [service
                      server
                      lrs
                      config]
  component/Lifecycle
  (start
   [this]
   (assert-config ::cs/webserver "webserver" config)
   (if server
     (do (log/info "Webserver already started; do nothing.")
         (log/tracef "Server map: %s" server)
         this)
     (if lrs
       (let [service (or service ;; accept passed in
                         (service-map lrs config))
             server  (-> service
                         i/xapi-default-interceptors
                         http/create-server
                         http/start)]
         (log/infof "Starting new webserver at host %s and port %s"
                    (::http/host service)
                    (::http/port service))
         (log/tracef "Server map: %s" server)
         (assoc this
                :service service
                :server server))
       (throw (ex-info "LRS Required to build service!"
                       {:type ::start-no-lrs
                        :webserver this})))))
  (stop
   [this]
   (if server
     (do (log/info "Stopping webserver...")
         (http/stop server)
         (assoc this
                :service nil
                :server nil
                :lrs nil))
     (do (log/info "Webserver already stopped; do nothing.")
         this))))
