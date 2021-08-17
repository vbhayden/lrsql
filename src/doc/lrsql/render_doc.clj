(ns lrsql.render-doc
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.string :as cstr]
            [markdown.core :as md]
            [markdown.transformers :as md-trans]
            [selmer.parser :as selm-parser])
  (:import [java.io File]))

;; Code is borrowed from: https://github.com/yetanalytics/third/blob/master/src/doc/render_docs.clj
;; Modified to take advantage of Selmer templates

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Markdown content -> HTML content
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn md-ext->html-ext
  "Convert the `.md` extension of `file-path` to `.html`."
  [file-path]
  (cstr/replace file-path #"\.md" ".html"))

(def relative-md-path-re
  #"\((?!www\.|(?:http|ftp)s?://|[A-Za-z]:\\|//)[A-Za-z0-9_\-\./]+\.md(?:#[0-9a-zA-Z\?\/\:\@\-\.\_\~\!\$\&\'\(\)\*\+\,\;\=]*)?\)")

(defn relative-md-links
  "Convert Markdown links in `text` into HTML links"
  [text state]
  [(cstr/replace text relative-md-path-re md-ext->html-ext)
   state])

(defn md->html
  "Wrapper for `md/md-to-html-string`."
  [text]
  (md/md-to-html-string
   text
   :heading-anchors true
   :replacement-transformers (cons relative-md-links
                                   md-trans/transformer-vector)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Markdown file -> HTML file
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def doc-template
  (-> "lrsql/doc/docs.html.template"
      io/resource
      selm-parser/parse*))

(defn fill-template
  "Add `content` to the HTML doc template."
  [content]
  (-> doc-template
      (selm-parser/render-template {:content content})))

(defn all-paths-seq
  "Return a seq of all files located in `root`."
  [root]
  (->> (io/file root)
       file-seq
       (remove #(.isDirectory ^File %))
       (map #(.getPath ^File %))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Markdown doc folder -> HTML doc folder
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -main
  "Given Markdown docs at `in-root`, create HTML docs in `out-root`."
  [in-root out-root]
  (let [in-root-regex (re-pattern (format "^%s" in-root))]
    (doseq [^String md-path (all-paths-seq in-root)
            :let [^String html-path (cstr/replace md-path
                                                  in-root-regex
                                                  out-root)]]
      ;; Make parent directory
      (io/make-parents html-path)
      ;; Make file
      (if (.endsWith md-path ".md")
        ;; Convert Markdown into HTML
        (let [html (-> md-path slurp md->html fill-template)]
          (spit (io/file (md-ext->html-ext html-path)) html))
        ;; Simply copy other, non-HTML files (e.g. images)
        (io/copy (io/file md-path)
                 (io/file html-path))))))

(comment
  (-main "doc" "target/bundle/doc")
  )