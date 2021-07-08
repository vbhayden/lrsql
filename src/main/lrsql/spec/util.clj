(ns lrsql.spec.util
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]))

(defn remove-nil-vals
  "Remove nil values from `m`. Useful when values could either be nil or
   not present in `m`."
  [m]
  (into {} (filter #(-> % second some?) m)))

(defn make-str-spec
  "Make a spec w/ gen capability for strings of a particular format."
  [spec conform-fn unform-fn]
  (s/with-gen
    (s/and string?
           (s/conformer conform-fn unform-fn)
           spec)
    #(sgen/fmap unform-fn
                (s/gen spec))))
