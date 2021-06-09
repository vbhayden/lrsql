(ns lrsql.spec.config
  (:require [clojure.spec.alpha :as s]
            [xapi-schema.spec :as xs]))

;; TODO: Add SQLite and Postgres at the very least
(s/def ::db-type #{"h2" "h2:mem"})
(s/def ::db-name string?)
(s/def ::db-host string?)
(s/def ::db-port nat-int?)
(s/def ::db-schema string?)
(s/def ::db-jdbc-url ::xs/iri)

(s/def ::database
  (s/keys :req-un [::db-type
                   ::db-name
                   ::db-host
                   ::db-port
                   ::db-schema]
          :opt-un [::db-jdbc-url]))

(s/def ::user string?)
(s/def ::password string?)
(s/def ::pool-init-size nat-int?)
(s/def ::pool-min-size nat-int?)
(s/def ::pool-inc nat-int?)
(s/def ::pool-max-size nat-int?)
(s/def ::pool-max-stmts nat-int?)

(s/def ::connection
  (s/and (s/keys :req-un [::database]
                 :opt-un [::user
                          ::password
                          ::pool-init-size
                          ::pool-min-size
                          ::pool-inc
                          ::pool-max-size
                          ::pool-max-stmts])
         (fn [{:keys [pool-min-size pool-max-size]
               :or {pool-min-size 3 ; c3p0 defaults
                    pool-max-size 15}}]
           (<= pool-min-size pool-max-size))))

(s/def ::stmt-more-url-prefix string?)
(s/def ::stmt-get-default pos-int?)
(s/def ::stmt-get-max pos-int?)

(s/def ::lrs
  (s/keys :req-un [::database
                   ::stmt-more-url-prefix
                   ::stmt-get-default
                   ::stmt-get-max]))

(s/def ::http-host string?)
(s/def ::http-port nat-int?)

(s/def ::webserver
  (s/keys :req-un [::http-host
                   ::http-port]))

(def config-spec
  (s/keys :req-un [::database
                   ::connection
                   ::lrs
                   ::webserver]))