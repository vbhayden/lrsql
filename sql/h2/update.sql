-- :name void-statement!
-- :command :execute
-- :result :affected
-- :doc Update a statement such that `is_voided` is set to true. Voiding statements themselves cannot be voided.
UPDATE xapi_statement
SET is_voided = TRUE
WHERE statement_id = :statement-id
AND NOT (verb_iri = 'http://adlnet.gov/expapi/verbs/voided')
-- ^ Any Statement that voids another cannot itself be voided.

-- :name update-state-document!
-- :command :insert
-- :result :affected
-- :doc Update the `document` and `last_modified` fields of a state document.
UPDATE state_document
SET document = :document, last_modified = :last-modified
WHERE state_id = :state-id
AND activity_iri = :activity-iri
AND agent_ifi = :agent-ifi
--~ (if (:?registration params) "AND registration = :?registration" "AND registration IS NULL")

-- :name update-agent-profile-document!
-- :command :insert
-- :result :affected
-- :doc Update the `document` and `last_modified` fields of an agent profile document.
UPDATE agent_profile_document
SET document = :document, last_modified = :last-modified
WHERE profile_id = :profile-id
AND agent_ifi = :agent-ifi

-- :name update-activity-profile-document!
-- :command :insert
-- :result :affected
-- :doc Update the `document` and `last_modified` fields of an activity profile document
UPDATE activity_profile_document
SET document = :document, last_modified = :last-modified
WHERE profile_id = :profile-id
AND activity_iri = :activity-iri