(ns lrsql.hugsql.input.agent
  (:require [clojure.spec.alpha :as s]
            [lrsql.hugsql.spec.actor :as hs]
            [lrsql.hugsql.util.actor :refer [actor->ifi]]))

(s/fdef agent-query-input
  :args (s/cat :params hs/get-agent-params)
  :ret hs/agent-query-spec)

(defn agent-query-input
  "Construct an input for `command/query-agent!`"
  [{agent :agent}]
  {:agent-ifi (actor->ifi agent)})