(ns lrsql.hugsql.util
  (:require [java-time :as jt]
            [clj-uuid]
            [com.yetanalytics.lrs.xapi.statements :as ss]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UUIDs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn generate-uuid
  "Return a new sequential UUID."
  []
  (clj-uuid/squuid))

(defn str->uuid
  "Parse a string into an UUID."
  [uuid-str]
  (java.util.UUID/fromString uuid-str))

(defn uuid->str
  "Convert a UUID into a string."
  [uuid]
  (clj-uuid/to-string uuid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Timestamps
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn current-time
  "Return the current time as a java.util.Instant timestamp."
  []
  (jt/instant))

(defn str->time
  "Parse a string into a java.util.Instant timestamp."
  [ts-str]
  (jt/instant ts-str))

(defn time->str
  "Convert a java.util.Instant timestamp into a string."
  [ts]
  (jt/format ts))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Statements
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; If a Statement lacks a version, the version MUST be set to 1.0.0
(def xapi-version "1.0.0")

;; TODO: more specific authority
(def lrsql-authority {"name" "LRSQL"
                      "objectType" "Agent"
                      "account" {"homepage" "http://localhost:8080"
                                 "name"     "LRSQL"}})

(defn prepare-statement
  "Prepare `statement` for LRS storage by coll-ifying context activities
   and setting missing id, timestamp, authority, version, and stored
   properties."
  [statement]
  (let [{:strs [id timestamp authority version]} statement]
    ;; first coll-ify context activities
    (cond-> (ss/fix-statement-context-activities statement)
      true ; stored is always set by the LRS
      (assoc "stored" (time->str (current-time)))
      (not id)
      (assoc "id" (uuid->str (generate-uuid)))
      (not timestamp)
      (assoc "timestamp" (time->str (current-time)))
      (not authority)
      (assoc "authority" lrsql-authority)
      (not version)
      (assoc "version" xapi-version))))