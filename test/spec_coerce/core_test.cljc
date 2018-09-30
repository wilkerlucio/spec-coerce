(ns spec-coerce.core-test
  #?(:cljs (:require-macros [cljs.test :refer [deftest testing is are run-tests]]))
  (:require
    #?(:clj [clojure.test :refer [deftest testing is are]])
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.test.check :as tc]
            [clojure.test.check.generators]
            [clojure.test.check.properties :as prop]
            [clojure.spec.test.alpha :as st]
    #?(:clj
            [clojure.test.check.clojure-test :refer [defspec]])
    #?(:cljs [clojure.test.check.clojure-test :refer-macros [defspec]])
            [spec-coerce.core :as sc])
  #?(:clj
     (:import (java.net URI))))

#?(:clj (st/instrument))

(s/def ::infer-int int?)
(s/def ::infer-and-spec (s/and int? #(> % 10)))
(s/def ::infer-and-spec-indirect (s/and ::infer-int #(> % 10)))
(s/def ::infer-form (s/coll-of int?))
(s/def ::infer-nilable (s/nilable int?))

#?(:clj (s/def ::infer-decimal? decimal?))

(sc/def ::some-coercion sc/parse-long)

(s/def ::first-layer int?)
(sc/def ::first-layer #(-> (sc/parse-long %) inc))

(s/def ::second-layer ::first-layer)
(s/def ::second-layer-and (s/and ::first-layer #(> % 10)))

(deftest test-coerce-from-registry
  (testing "it uses the registry to coerce a key"
    (is (= (sc/coerce ::some-coercion "123") 123)))

  (testing "it returns original value when it a coercion can't be found"
    (is (= (sc/coerce ::not-defined "123") "123")))

  (testing "go over nilables"
    (is (= (sc/coerce ::infer-nilable "123") 123))))

(deftest test-coerce-from-predicates
  (are [predicate input output] (= (sc/coerce predicate input) output)
    `number? "42" 42.0
    `number? "foo" "foo"
    `integer? "42" 42
    `int? "42" 42
    `pos-int? "42" 42
    `neg-int? "-42" -42
    `nat-int? "10" 10
    `even? "10" 10
    `odd? "9" 9
    `float? "42.42" 42.42
    `double? "42.42" 42.42
    `boolean? "true" true
    `boolean? "false" false
    `ident? ":foo/bar" :foo/bar
    `ident? "foo/bar" 'foo/bar
    `simple-ident? ":foo" :foo
    `qualified-ident? ":foo/baz" :foo/baz
    `keyword? "keyword" :keyword
    `keyword? ":keyword" :keyword
    `simple-keyword? ":simple-keyword" :simple-keyword
    `qualified-keyword? ":qualified/keyword" :qualified/keyword
    `symbol? "sym" 'sym
    `simple-symbol? "simple-sym" 'simple-sym
    `qualified-symbol? "qualified/sym" 'qualified/sym
    `uuid? "d6e73cc5-95bc-496a-951c-87f11af0d839" #uuid "d6e73cc5-95bc-496a-951c-87f11af0d839"
    `nil? "nil" nil
    `nil? "null" nil
    `false? "false" false
    `true? "true" true
    `zero? "0" 0

    `(s/coll-of int?) ["11" "31" "42"] [11 31 42]
    `(s/coll-of int?) ["11" "foo" "42"] [11 "foo" 42]

    `(s/map-of keyword? int?) {"foo" "42" "bar" "31"} {:foo 42 :bar 31}
    `(s/map-of keyword? int?) "foo" "foo"

    `(s/or :int int? :double double? :bool boolean?) "42" 42
    `(s/or :double double? :bool boolean?) "42.3" 42.3
    `(s/or :int int? :double double? :bool boolean?) "true" true

    #?@(:clj [`uri? "http://site.com" (URI. "http://site.com")])
    #?@(:clj [`decimal? "42.42" 42.42M
              `decimal? "42.42M" 42.42M])))

(def test-gens
  {`inst? (s/gen (s/inst-in #inst "1980" #inst "9999"))})

#?(:cljs
   (defn ->js [var-name]
     (-> (str var-name)
         (str/replace #"/" ".")
         (str/replace #"-" "_")
         (str/replace #"\?" "_QMARK_")
         (js/eval))))

(defn safe-gen [s sp]
  (try
    (or (test-gens s) (s/gen sp))
    (catch #?(:clj Exception :cljs :default) _ nil)))

(deftest test-coerce-generative
  (doseq [s (->> (methods sc/sym->coercer)
                 (keys)
                 (filter symbol?))
          :let [sp #?(:clj @(resolve s)
                      :cljs (->js s))
                gen        (safe-gen s sp)]
          :when gen]
    (let [res (tc/quick-check 100
                (prop/for-all [v gen]
                  (s/valid? sp (sc/coerce s (-> (pr-str v)
                                                (str/replace #"^#[^\"]+\"|\"]?$"
                                                             ""))))))]
      (if-not (= true (:result res))
        (throw (ex-info (str "Error coercing " s)
                        {:symbol s
                         :result res}))))))

#?(:clj (deftest test-coerce-inst
          ;; use .getTime to avoid java.sql.Timestamp/java.util.Date differences
          ;; we don't check s/valid? here, just that the date/time roundtrips
          (are [input output] (= (.getTime (sc/coerce `inst? input))
                                 (.getTime output))
            "9/28/2018 22:06" #inst "2018-09-28T22:06"
            (str "Fri Sep 28 22:06:52 "
                 (.getID (java.util.TimeZone/getDefault))
                 " 2018")     #inst "2018-09-28T22:06:52"
            "2018-09-28"      #inst "2018-09-28"
            "9/28/2018"       #inst "2018-09-28")))

(deftest test-coerce-inference-test
  (are [keyword input output] (= (sc/coerce keyword input) output)
    ::infer-int "123" 123
    ::infer-and-spec "42" 42
    ::infer-and-spec-indirect "43" 43
    ::infer-form ["20" "43"] [20 43]
    ::second-layer "41" 42
    ::second-layer-and "41" 42

    #?@(:clj [::infer-decimal? "123.4" 123.4M])))

(deftest test-coerce-structure
  (is (= (sc/coerce-structure {::some-coercion "321"
                               ::not-defined   "bla"
                               :sub            {::infer-int "42"}})
         {::some-coercion 321
          ::not-defined   "bla"
          :sub            {::infer-int 42}}))
  (is (= (sc/coerce-structure {::some-coercion "321"
                               ::not-defined   "bla"
                               :unqualified    "12"
                               :sub            {::infer-int "42"}}
                              {::sc/overrides {::not-defined `keyword?
                                               :unqualified  ::infer-int}})
         {::some-coercion 321
          ::not-defined   :bla
          :unqualified    12
          :sub            {::infer-int 42}})))

(s/def ::bool boolean?)
(s/def ::simple-keys (s/keys :req [::infer-int]
                             :opt [::bool]))
(s/def ::nested-keys (s/keys :req [::infer-form ::simple-keys]
                             :req-un [::bool]))

(deftest test-coerce-keys
  (is (= {::infer-int 123}
         (sc/coerce ::simple-keys {::infer-int "123"})))
  (is (= {::infer-form [1 2 3]
          ::simple-keys   {::infer-int 456
                           ::bool      true}
          :bool true}
         (sc/coerce ::nested-keys {::infer-form  ["1" "2" "3"]
                                   ::simple-keys {::infer-int "456"
                                                  ::bool      "true"}
                                   :bool         "true"}))))
