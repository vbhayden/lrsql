(ns lrsql.hugsql.input
  (:require [clojure.spec.alpha :as s]
            [clj-uuid :as uuid]
            [java-time :as jt]
            [hugsql.core :as hugsql]
            [xapi-schema.spec :as xs]
            [com.yetanalytics.lrs.xapi.statements :as ss]
            [lrsql.hugsql.spec :as hs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- current-time
  "Return the current time as a java.util.Instant object."
  []
  (jt/instant))

(defn- generate-uuid
  "Return a new sequential UUID."
  []
  (uuid/squuid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Declarations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Shut up VSCode warnings
(declare statement-id-snip)
(declare is-voided-snip)
(declare verb-iri-snip)
(declare registration-snip)
(declare timestamp-since-snip)
(declare timestamp-until-snip)
(declare statement-to-agent-join-snip)
(declare statement-to-activity-join-snip)
(declare actor-agent-usage-snip)
(declare object-activity-usage-snip)
(declare limit-snip)

;; (hugsql/def-db-fns "h2/h2_insert.sql")
(hugsql/def-db-fns "h2/h2_query.sql")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Store
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; /* Need explicit properties for querying Agents Resource */
;; Agent
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - Name: STRING
;; - Mbox: STRING
;; - MboxSHA1Sum: STRING
;; - OpenID: STRING
;; - AccountName: STRING
;; - AccountHomepage: STRING
;; - IsIdentifiedGroup: BOOLEAN NOT NULL DEFAULT FALSE -- Treat Identified Groups as Agents

(defn- get-ifi
  "Returns a map between the IFI type and the IFI of `obj` if it is an Actor
   or an Identified Group."
  [obj]
  (when (#{"Agent" "Group"} (get obj "objectType"))
    (not-empty (select-keys obj ["mbox"
                                 "mbox_sha1sum"
                                 "openid"
                                 "account"]))))

(s/fdef agent->insert-input
  :args (s/cat :agent (s/alt :agent ::xs/agent
                             :group ::xs/group))
  :ret hs/agent-insert-spec)

(defn agent->insert-input
  [agent]
  (when-some [ifi-m (get-ifi agent)]
    (cond-> {:table       :agent
             :primary-key (generate-uuid)
             :ifi         ifi-m}
      (contains? agent "name")
      (assoc :name (get agent "name"))
      (= "Group" (get agent "objectType"))
      (assoc :identified-group? true))))

;; Activity
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - ActivityIRI: STRING UNIQUE KEY NOT NULL
;; - Data: JSON NOT NULL

(s/fdef activity->insert-input
  :args (s/cat :activity ::xs/activity)
  :ret hs/activity-insert-spec)

(defn activity->insert-input
  [activity]
  {:table          :activity
   :primary-key    (generate-uuid)
   :activity-iri   (get activity "id")
   :payload        activity})

;; Attachment
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - SHA2: STRING UNIQUE KEY NOT NULL
;; - ContentType: STRING NOT NULL
;; - FileURL: STRING NOT NULL -- Either an external URL or the URL to a LRS location
;; - Data: BINARY NOT NULL

(s/fdef attachment->insert-input
  :args (s/cat :attachment ::ss/attachment)
  :ret hs/attachment-insert-spec)

(defn attachment->insert-input
  [{content      :content
    content-type :contentType
    sha2         :sha2}]
  {:table          :attachment
   :primary-key    (generate-uuid)
   :attachment-sha sha2
   :content-type   content-type
   :file-url       "" ; TODO
   :payload        content})

;; Statement-to-Agent
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - StatementKey: UUID NOT NULL
;; - StatementID: UUID NOT NULL
;; - Usage: STRING IN ('Actor', 'Object', 'Authority', 'Instructor', 'Team') NOT NULL
;; - AgentKey: UUID NOT NULL
;; - AgentIFI: STRING NOT NULL
;; - AgentIFIType: STRING IN ('Mbox', 'MboxSHA1Sum', 'OpenID', 'Account') NOT NULL

(s/fdef agent-input->link-input
  :args (s/cat :statement-id ::hs/statement-id
               :agent-usage :lrsql.hugsql.spec.activity/usage
               :agent-input hs/agent-insert-spec)
  :ret hs/statement-to-agent-insert-spec)

(defn- agent-input->link-input
  [statement-id agent-usage {agent-ifi :ifi}]
  {:table        :statement-to-agent
   :primary-key  (generate-uuid)
   :statement-id statement-id
   :usage        agent-usage
   :ifi          agent-ifi})

;; Statement-to-Activity
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - StatementKey: UUID NOT NULL
;; - StatementID: UUID NOT NULL
;; - Usage: STRING IN ('Object', 'Category', 'Grouping', 'Parent', 'Other') NOT NULL
;; - ActivityKey: UUID NOT NULL
;; - ActivityIRI: STRING NOT NULL

(s/fdef activity-input->link-input
  :args (s/cat :statement-id ::hs/statement-id
               :activity-usage :lrsql.hugsql.spec.activity/usage
               :activity-input hs/activity-insert-spec)
  :ret hs/statement-to-activity-insert-spec)

(defn- activity-input->link-input
  [statement-id activity-usage {activity-id :activity-iri}]
  {:table        :statement-to-activity
   :primary-key  (generate-uuid)
   :statement-id statement-id
   :usage        activity-usage
   :activity-iri activity-id})

;; Statement-to-Attachment
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - StatementKey: UUID NOT NULL
;; - StatementID: UUID NOT NULL
;; - AttachmentKey: UUID NOT NULL
;; - AttachemntSHA2: STRING NOT NULL

(s/fdef attachment-input->link-input
  :args (s/cat :statement-id ::hs/statement-id
               :attachment-input hs/attachment-insert-spec)
  :ret hs/statement-to-attachment-insert-spec)

(defn- attachment-input->link-input
  [statement-id {attachment-id :attachment-sha}]
  {:table           :statement-to-attachment
   :primary-key     (generate-uuid)
   :statement-id    statement-id
   :attachment-sha2 attachment-id})

;; Statement
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - StatementID: UUID UNIQUE KEY NOT NULL
;; - SubStatementID: UUID
;; - StatementRefID: UUID
;; - Timestamp: TIMESTAMP NOT NULL
;; - Stored: TIMESTAMP NOT NULL
;; - Registration: UUID
;; - VerbID: STRING NOT NULL
;; - IsVoided: BOOLEAN NOT NULL DEFAULT FALSE
;; - Data: JSON NOT NULL

(s/fdef statement->insert-input
  :args (s/cat :statement ::xs/statement
               :?attachments (s/nilable (s/coll-of ::ss/attachment
                                                   :min-count 1)))
  :ret (s/cat
        :statement-input hs/statement-insert-spec
        :agent-inputs (s/* hs/agent-insert-spec)
        :activity-inputs (s/* hs/activity-insert-spec)
        :stmt-agent-inputs (s/* hs/statement-to-agent-insert-spec)
        :stmt-activity-inputs (s/* hs/statement-to-activity-insert-spec)
        :stmt-attachment-inputs (s/* hs/statement-to-attachment-insert-spec)))

(defn statement->insert-input
  [statement ?attachments]
  (let [stmt-pk      (generate-uuid)
        stmt-id      (get statement "id")
        stmt-obj     (get statement "object")
        stmt-ctx     (get statement "context")
        stmt-time    (if-let [ts (get statement "timestamp")] ts (current-time))
        stmt-stored  (current-time)
        stmt-reg     (get-in statement ["context" "registration"])
        sub-stmt-id  (when (= "SubStatement" (get stmt-obj "objectType"))
                       (generate-uuid))
        stmt-ref-id  (when (= "StatementRef" (get stmt-obj "objectType"))
                       (get stmt-obj "id"))
        stmt-vrb-id  (get-in statement ["verb" "id"])
        voided?      (= "http://adlnet.gov/expapi/verbs/voided" stmt-vrb-id)
        ;; Statement Agents
        stmt-actr    (get statement "actor")
        stmt-auth    (get statement "authority")
        stmt-inst    (get stmt-ctx "instructor")
        stmt-team    (get stmt-ctx "team")
        obj-agnt-in  (agent->insert-input stmt-obj)
        actr-agnt-in (agent->insert-input stmt-actr)
        auth-agnt-in (agent->insert-input stmt-auth)
        inst-agnt-in (agent->insert-input stmt-inst)
        team-agnt-in (agent->insert-input stmt-team)
        ;; Statement Activities
        cat-acts     (get-in stmt-ctx ["contextActivities" "category"])
        grp-acts     (get-in stmt-ctx ["contextActivities" "grouping"])
        prt-acts     (get-in stmt-ctx ["contextActivities" "parent"])
        oth-acts     (get-in stmt-ctx ["contextActivities" "other"])
        obj-act-in   (when (= "Activity" (get stmt-obj "objectType"))
                       (activity->insert-input stmt-obj))
        cat-acts-in  (when cat-acts (map activity->insert-input cat-acts))
        grp-acts-in  (when grp-acts (map activity->insert-input grp-acts))
        prt-acts-in  (when prt-acts (map activity->insert-input prt-acts))
        oth-acts-in  (when oth-acts (map activity->insert-input oth-acts))
        ;; Statement HugSql input
        stmt-input   {:table             :statement
                      :primary-key       stmt-pk
                      :statement-id      stmt-id
                      :?sub-statement-id sub-stmt-id
                      :?statement-ref-id stmt-ref-id
                      :timestamp         stmt-time
                      :stored            stmt-stored
                      :?registration     stmt-reg
                      :verb-iri          stmt-vrb-id
                      :voided?           voided?
                      :payload           statement}
        ;; Agent HugSql input
        agnt-inputs  (cond-> []
                       actr-agnt-in (conj actr-agnt-in)
                       obj-agnt-in  (conj obj-agnt-in)
                       auth-agnt-in (conj auth-agnt-in)
                       inst-agnt-in (conj inst-agnt-in)
                       team-agnt-in (conj team-agnt-in))
        ;; Activity HugSql input
        act-inputs   (cond-> []
                       obj-act-in  (conj obj-act-in)
                       cat-acts-in (concat cat-acts-in)
                       grp-acts-in (concat grp-acts-in)
                       prt-acts-in (concat prt-acts-in)
                       oth-acts-in (concat oth-acts-in))
        ;; Attachment HugSql input
        att-inputs   (when ?attachments
                       (map attachment->insert-input ?attachments))
        ;; Statement-to-Agent HugSql input
        agent->link  (partial agent-input->link-input stmt-id)
        stmt-agnts   (cond-> []
                       actr-agnt-in
                       (conj (agent->link "Actor" actr-agnt-in))
                       obj-agnt-in
                       (conj (agent->link "Object" obj-agnt-in))
                       auth-agnt-in
                       (conj (agent->link "Authority" auth-agnt-in))
                       inst-agnt-in
                       (conj (agent->link "Instructor" inst-agnt-in))
                       team-agnt-in
                       (conj (agent->link "Team" team-agnt-in)))
        ;; Statement-to-Activity HugSql input
        act->link    (partial activity-input->link-input stmt-id)
        stmt-acts    (cond-> []
                       obj-act-in
                       (conj (act->link "Object" obj-act-in))
                       cat-acts-in
                       (concat (map (partial act->link "Category") cat-acts-in))
                       grp-acts-in
                       (concat (map (partial act->link "Grouping") grp-acts-in))
                       prt-acts-in
                       (concat (map (partial act->link "Parent") prt-acts-in))
                       oth-acts-in
                       (concat (map (partial act->link "Other") oth-acts-in)))
        ;; Statement-to-Attachment HugSql input
        stmt-atts    (when att-inputs
                       (map (partial attachment-input->link-input stmt-id)
                            att-inputs))]
    (concat [stmt-input]
            agnt-inputs
            act-inputs
            att-inputs
            stmt-agnts
            stmt-acts
            stmt-atts)))

;; State-Document
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - StateID: STRING NOT NULL
;; - ActivityID: STRING NOT NULL
;; - AgentID: UUID NOT NULL
;; - Registration: UUID
;; - LastModified: TIMESTAMP NOT NULL
;; - Document: BINARY NOT NULL
;;
;; Agent-Profile-Document
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - ProfileID: STRING NOT NULL
;; - AgentID: UUID NOT NULL
;; - LastModified: TIMESTAMP NOT NULL
;; - Document: BINARY NOT NULL
;;
;; Activity-Profile-Resource
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - ProfileID: STRING NOT NULL
;; - ActivityID: STRING NOT NULL
;; - LastModified: TIMESTAMP NOT NULL
;; - Document: BINARY NOT NULL

(s/fdef document->hugsql-input
  :args (s/cat
         :id-params
         (s/alt :state :xapi.document.state/id-params
                :agent-profile :xapi.document.agent-profile/id-params
                :activity-profile :xapi.document.activity-profile/id-params)
         :document any?) ; TODO: bytes? predicate
  :ret (s/or :state hs/state-document-insert-spec
             :agent-profile hs/agent-profile-document-insert-spec
             :activity-profile hs/activity-profile-document-insert-spec))

(defn document->insert-input
  [{state-id     :stateID
    profile-id   :profileID
    activity-id  :activityID
    agent        :agent
    registration :registration
    :as          _id-params}
   ^bytes document]
  (cond
    ;; State Document
    state-id
    {:table         :state-document
     :primary-key   (generate-uuid)
     :state-id      state-id
     :activity-id   activity-id
     :agent-id      (get-ifi agent)
     :?registration registration
     :last-modified (current-time)
     :document      document}

    ;; Agent Profile Document
    (and profile-id agent)
    {:table         :agent-profile-document
     :primary-key   (generate-uuid)
     :profile-id    profile-id
     :agent-id      (get-ifi agent)
     :last-modified (current-time)
     :document      document}

    ;; Activity Profile Document
    (and profile-id activity-id)
    {:table         :activity-profile-document
     :primary-key   (generate-uuid)
     :profile-id    profile-id
     :activity-id   activity-id
     :last-modified (current-time)
     :document      document}))

;; TODO
;; Canonical-Language-Maps
;; - ID: UUID PRIMARY KEY NOT NULL AUTOINCREMENT
;; - IRI: STRING UNIQUE KEY NOT NULL
;; - LangTag: STRING NOT NULL
;; - Value: STRING NOT NULL

;; #_{:clj-kondo/ignore [:unresolved-symbol]}
;; (defn insert-hugsql-inputs!
;;   [inputs db]
;;   (doseq [{:keys [table] :as input} inputs]
;;     (case table
;;       :statement
;;       (insert-statement db input)
;;       :agent
;;       (insert-agent db input)
;;       :activity
;;       (insert-activity db input)
;;       :attachment
;;       (insert-activity db input)
;;       :statement-to-agent
;;       (insert-statement-to-agent db input)
;;       :statement-to-activity
;;       (insert-statement-to-activity db input)
;;       :statement-to-attachment
;;       (insert-statement-to-attachment db input))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Query
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: format
;; TODO: attachments

(s/fdef query-params
  :args (s/cat :params :xapi.statements.GET.request/params)
  :ret hs/statement-query-spec)

; {:clj-kondo/ignore [:unresolved-symbol]}
(defn query-params->query-input
  [{statement-id        :statementId
    voided-statement-id :voidedStatementId
    verb                :verb
    agent               :agent
    activity            :activity
    registration        :registration
    related-activities? :related_activities
    related-agents?     :related_agents
    since               :since
    until               :until
    limit               :limit
    attachments?        :attachments
    ascending?          :ascending
    page                :page
    from                :from}]
  (cond-> {}
    statement-id
    (merge {:statement-id-snip
            (statement-id-snip {:statement-id statement-id})
            :is-voided-snip
            (is-voided-snip {:voided? false})})
    voided-statement-id
    (merge {:statement-id-snip
            (statement-id-snip {:statement-id voided-statement-id})
            :is-voided-snip
            (is-voided-snip {:voided? true})})
    verb
    (assoc :verb-iri-snip
           (verb-iri-snip {:verb-iri verb}))
    registration
    (assoc :registration-snip
           (registration-snip {:registration registration}))
    since
    (assoc :timestamp-since-snip
           (timestamp-since-snip {:since since}))
    until
    (assoc :timestamp-until-snip
           (timestamp-until-snip {:until until}))
    agent
    (assoc :statement-to-agent-join-snip
           (statement-to-agent-join-snip
            (cond-> {:agent-ifi (get-ifi agent)}
              (not related-agents?)
              (assoc :actor-agent-usage-snip
                     (actor-agent-usage-snip)))))
    activity
    (assoc :statement-to-activity-join-snip
           (statement-to-activity-join-snip
            (cond-> {:activity-iri activity}
              (not related-activities?)
              (assoc :object-activity-usage-snip
                     (object-activity-usage-snip)))))
    limit
    (assoc :limit-snip
           (limit-snip {:limit limit}))))