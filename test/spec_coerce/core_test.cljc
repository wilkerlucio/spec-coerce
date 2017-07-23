(ns spec-coerce.core-test
  #?(:cljs (:require-macros [cljs.test :refer [deftest testing is are run-tests]]))
  (:require
    #?(:clj [clojure.test :refer [deftest testing is are]])
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.test.check :as tc]
            [clojure.test.check.generators]
            [clojure.test.check.properties :as prop]
    #?(:clj
            [clojure.test.check.clojure-test :refer [defspec]])
    #?(:cljs [clojure.test.check.clojure-test :refer-macros [defspec]])
            [spec-coerce.core :as sc])
  #?(:clj
     (:import (java.net URI))))

(s/def ::infer-int int?)
(s/def ::infer-and-spec (s/and int? #(> % 10)))
(s/def ::infer-and-spec-indirect (s/and ::infer-int #(> % 10)))
(s/def ::infer-form (s/coll-of int?))

#?(:clj (s/def ::infer-bigdec? bigdec?))

(sc/def ::some-coercion sc/parse-long)

(s/def ::first-layer int?)
(sc/def ::first-layer #(-> (sc/parse-long %) inc))

(s/def ::second-layer ::first-layer)
(s/def ::second-layer-and (s/and ::first-layer #(> % 10)))

(deftest test-coerce-from-registry
  (testing "it uses the registry to coerce a key"
    (is (= (sc/coerce ::some-coercion "123") 123)))

  (testing "it returns original value when it a coercion can't be found"
    (is (= (sc/coerce ::not-defined "123") "123"))))

(deftest test-coerce-from-predicates
  (are [predicate input output] (= (sc/coerce predicate input) output)
    `number? "42" 42.0
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
    `(s/coll-of int?) ["11" "31.2" "42"] [11 "31.2" 42]

    `(s/or :int int? :double double? :bool boolean?) "42" 42
    `(s/or :int int? :double double? :bool boolean?) "42.3" 42.3
    `(s/or :int int? :double double? :bool boolean?) "true" true

    #?@(:clj [`uri? "http://site.com" (URI. "http://site.com")])
    #?@(:clj [`bigdec? "42.42" 42.42M
              `bigdec? "42.42M" 42.42M])))

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
                gen (safe-gen s sp)]
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

(deftest test-coerce-inference-test
  (are [keyword input output] (= (sc/coerce keyword input) output)
    ::infer-int "123" 123
    ::infer-and-spec "42" 42
    ::infer-and-spec-indirect "43" 43
    ::infer-form ["20" "43"] [20 43]
    ::second-layer "41" 42
    ::second-layer-and "41" 42

    #?@(:clj [::infer-bigdec? "123.4" 123.4M])))

(deftest test-coerce-structure
  (is (= (sc/coerce-structure {::some-coercion "321"
                               ::not-defined   "bla"
                               :sub            {::infer-int "42"}})
         {::some-coercion 321
          ::not-defined   "bla"
          :sub            {::infer-int 42}})))
