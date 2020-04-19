#!/usr/bin/env bb

(defn xml-seq [path]
  (->> (xml/parse-str (slurp path))
       (tree-seq :content :content)))

(defn tag-name? [tag tname]
  (some-> tag :tag name #{tname}))

(defn tag-content-str [tag]
  (->> tag :content (filter string?) (str/join "")))

(defn pom-version
  ([] (pom-version "pom.xml"))
  ([path]
   (->> (xml-seq path)
        (filter #(tag-name? % "version"))
        first
        tag-content-str)))

(defn prompt
  ([question] (prompt question identity))
  ([question coercion]
   (print (str question ": "))
   (flush)
   (let [response (str/trim (read-line))]
     (coercion response))))

(defn tag-current-version-from-pom []
  (let [version (str "v" (pom-version))]
    (println "Creating tag...")
    (shell/sh "git" "tag" "-a" version "-m" version)
    (shell/sh "git" "push" "--follow-tags")))

(let [login    (prompt "Clojars username")
      password (prompt "Clojars password")
      new-env  (assoc (into {} (System/getenv)) "CLOJARS_USERNAME" login "CLOJARS_PASSWORD" password)]
  (tag-current-version-from-pom)
  (println "Packing...")
  (shell/sh "clojure" "-A:pack")
  (println "Deploying...")
  (println (:out (shell/sh "clojure" "-A:deploy" :env new-env))))
