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
(sc/def ::first-layer (fn [x _] (inc (sc/parse-long x))))

(s/def ::second-layer ::first-layer)
(s/def ::second-layer-and (s/and ::first-layer #(> % 10)))

(s/def ::or-example (s/or :int int? :double double? :bool boolean?))

(s/def ::nilable-int (s/nilable ::infer-int))
(s/def ::nilable-pos-int (s/nilable (s/and ::infer-int pos?)))
(s/def ::nilable-string (s/nilable string?))

(s/def ::int-set #{1 2})
(s/def ::float-set #{1.2 2.1})
(s/def ::boolean-set #{true})
(s/def ::symbol-set #{'foo/bar 'bar/foo})
(s/def ::ident-set #{'foo/bar :bar/foo})
(s/def ::string-set #{"hey" "there"})
(s/def ::keyword-set #{:a :b})
(s/def ::uuid-set #{#uuid "d6e73cc5-95bc-496a-951c-87f11af0d839"
                    #uuid "a6e73cc5-95bc-496a-951c-87f11af0d839"})
(s/def ::nil-set #{nil})
#?(:clj (s/def ::uri-set #{(URI. "http://site.com")
                           (URI. "http://site.org")}))
#?(:clj (s/def ::decimal-set #{42.42M 1.1M}))

(def enum-set #{:a :b})
(s/def ::referenced-set enum-set)

(def enum-map {:foo "bar"
               :baz "qux"})
(s/def ::calculated-set (->> enum-map keys (into #{})))

(s/def ::nilable-referenced-set (s/nilable enum-set))
(s/def ::nilable-calculated-set (s/nilable (->> enum-map keys (into #{}))))

(s/def ::nilable-referenced-set-kw (s/nilable ::referenced-set))
(s/def ::nilable-calculated-set-kw (s/nilable ::calculated-set))

(s/def ::unevaluatable-spec (letfn [(pred [x] (string? x))]
                              (s/spec pred)))

(deftest test-coerce-from-registry
  (testing "it uses the registry to coerce a key"
    (is (= (sc/coerce ::some-coercion "123") 123)))

  (testing "it returns original value when it a coercion can't be found"
    (is (= (sc/coerce ::not-defined "123") "123")))

  (testing "go over nilables"
    (is (= (sc/coerce ::infer-nilable "123") 123))
    (is (= (sc/coerce ::infer-nilable "nil") nil))
    (is (= (sc/coerce ::nilable-int "10") 10))
    (is (= (sc/coerce ::nilable-pos-int "10") 10))

    (is (= (sc/coerce ::nilable-string nil) nil))
    (is (= (sc/coerce ::nilable-string 1) "1"))
    (is (= (sc/coerce ::nilable-string "") ""))
    (is (= (sc/coerce ::nilable-string "asdf") "asdf")))

  (testing "specs given as sets"
    (is (= (sc/coerce ::int-set "1") 1))
    (is (= (sc/coerce ::float-set "1.2") 1.2))
    (is (= (sc/coerce ::boolean-set "true") true))
    ;;(is (= (sc/coerce ::symbol-set "foo/bar") 'foo/bar))
    (is (= (sc/coerce ::string-set "hey") "hey"))
    (is (= (sc/coerce ::keyword-set ":b") :b))
    (is (= (sc/coerce ::uuid-set "d6e73cc5-95bc-496a-951c-87f11af0d839") #uuid "d6e73cc5-95bc-496a-951c-87f11af0d839"))
    (is (= (sc/coerce ::nil-set "nil") nil))
    ;;#?(:clj (is (= (sc/coerce ::uri-set "http://site.com") (URI. "http://site.com"))))
    #?(:clj (is (= (sc/coerce ::decimal-set "42.42M") 42.42M)))

    ;; The following tests can't work without using `eval`. We will avoid this
    ;; and hope that spec2 will give us a better way.
    ;;(is (= (sc/coerce ::referenced-set ":a") :a))
    ;;(is (= (sc/coerce ::calculated-set ":foo") :foo))
    ;;(is (= (sc/coerce ::nilable-referenced-set ":a") :a))
    ;;(is (= (sc/coerce ::nilable-calculated-set ":foo") :foo))
    ;;(is (= (sc/coerce ::nilable-referenced-set-kw ":a") :a))
    ;;(is (= (sc/coerce ::nilable-calculated-set-kw ":foo") :foo))

    (is (= (sc/coerce ::unevaluatable-spec "just a string") "just a string"))))

(deftest test-coerce!
  (is (= (sc/coerce! ::infer-int "123") 123))
  (is (= (sc/coerce! :infer-int "123") "123"))
  (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) #"Failed to coerce value" (sc/coerce! ::infer-int "abc"))))

(deftest test-conform
  (is (= (sc/conform ::or-example "true") [:bool true])))

(deftest test-coerce-from-predicates
  (are [predicate input output] (= (sc/coerce predicate input) output)
    `number? "42" 42.0
    `number? "foo" "foo"
    `integer? "42" 42
    `int? "42" 42
    `int? 42.0 42
    `int? 42.5 42
    `pos-int? "42" 42
    `neg-int? "-42" -42
    `nat-int? "10" 10
    `even? "10" 10
    `odd? "9" 9
    `float? "42.42" 42.42
    `double? "42.42" 42.42
    `double? 42.42 42.42
    `double? 42 42.0
    `string? 42 "42"
    `string? :a ":a"
    `string? :foo/bar ":foo/bar"
    `boolean? "true" true
    `boolean? "false" false
    `ident? ":foo/bar" :foo/bar
    `ident? "foo/bar" 'foo/bar
    `simple-ident? ":foo" :foo
    `qualified-ident? ":foo/baz" :foo/baz
    `keyword? "keyword" :keyword
    `keyword? ":keyword" :keyword
    `keyword? 'symbol :symbol
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
                                " 2018") #inst "2018-09-28T22:06:52"
                              "2018-09-28" #inst "2018-09-28"
                              "9/28/2018" #inst "2018-09-28")))

(deftest test-coerce-inference-test
  (are [keyword input output] (= (sc/coerce keyword input) output)
    ::infer-int "123" 123
    ::infer-and-spec "42" 42
    ::infer-and-spec-indirect "43" 43
    ::infer-form ["20" "43"] [20 43]
    ::infer-form '("20" "43") '(20 43)
    ::infer-form (map str (range 2)) '(0 1)
    ::second-layer "41" 42
    ::second-layer-and "41" 42

    #?@(:clj [::infer-decimal? "123.4" 123.4M])
    #?@(:clj [::infer-decimal? 123.4 123.4M])
    #?@(:clj [::infer-decimal? 123.4M 123.4M])
    #?@(:clj [::infer-decimal? "" ""])
    #?@(:clj [::infer-decimal? [] []])))

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
          :sub            {::infer-int 42}}))
  (is (= (sc/coerce-structure {::or-example "321"}
           {::sc/op sc/conform})
         {::or-example [:int 321]})))

(s/def ::bool boolean?)
(s/def ::simple-keys (s/keys :req [::infer-int]
                       :opt [::bool]))
(s/def ::nested-keys (s/keys :req [::infer-form ::simple-keys]
                       :req-un [::bool]))

(deftest test-coerce-keys
  (is (= {::infer-int 123}
         (sc/coerce ::simple-keys {::infer-int "123"})))
  (is (= {::infer-form  [1 2 3]
          ::simple-keys {::infer-int 456
                         ::bool      true}
          :bool         true}
         (sc/coerce ::nested-keys {::infer-form  ["1" "2" "3"]
                                   ::simple-keys {::infer-int "456"
                                                  ::bool      "true"}
                                   :bool         "true"})))
  (is (= "garbage" (sc/coerce ::simple-keys "garbage"))))

(s/def ::head double?)
(s/def ::body int?)
(s/def ::arm  int?)
(s/def ::leg  double?)
(s/def ::arms (s/coll-of ::arm))
(s/def ::legs (s/coll-of ::leg))
(s/def ::name string?)
(s/def ::animal (s/keys :req    [::head ::body ::arms ::legs]
                        :opt-un [::name ::id]))

(deftest test-coerce-with-registry-overrides
  (testing "it uses overrides when provided"
    (is (= {::head 1 ::body 16 ::arms [4 4] ::legs [7 7] :name :john}
           (binding [sc/*overrides* {::head (sc/sym->coercer `int?)
                                     ::leg  (sc/sym->coercer `int?)
                                     ::name (sc/sym->coercer `keyword?)}]
             (sc/coerce ::animal {::head "1"
                                  ::body "16"
                                  ::arms ["4" "4"]
                                  ::legs ["7" "7"]
                                  :name "john"}))))
    (is (= {::head 1
            ::body 16
            ::arms [4 4]
            ::legs [7 7]
            :name :john}
           (sc/coerce ::animal
                      {::head "1"
                       ::body "16"
                       ::arms ["4" "4"]
                       ::legs ["7" "7"]
                       :name "john"}
                      {::sc/overrides
                       {::head (sc/sym->coercer `int?)
                        ::leg  (sc/sym->coercer `int?)
                        ::name (sc/sym->coercer `keyword?)}})))
    "Coerce with option form"))

(s/def ::foo int?)
(s/def ::bar string?)
(s/def ::qualified (s/keys :req [(or ::foo ::bar)]))
(s/def ::unqualified (s/keys :req-un [(or ::foo ::bar)]))

(deftest test-or-conditions-in-qualified-keys
  (is (= (sc/coerce ::qualified {::foo "1" ::bar "hi"})
         {::foo 1 ::bar "hi"})))

(deftest test-or-conditions-in-unqualified-keys
  (is (= (sc/coerce ::unqualified {:foo "1" :bar "hi"})
         {:foo 1 :bar "hi"})))

(s/def ::tuple (s/tuple ::foo ::bar int?))

(deftest test-tuple
  (is (= [0 "" 1] (sc/coerce ::tuple ["0" nil "1"])))
  (is (= "garbage" (sc/coerce ::tuple "garbage"))))

(deftest test-merge
  (s/def ::merge (s/merge (s/keys :req-un [::foo])
                          ::unqualified
                          ;; TODO: add s/multi-spec test
                          ))
  (is (= {:foo 1 :bar "1" :c {:a 2}}
         (sc/coerce ::merge {:foo "1" :bar 1 :c {:a 2}}))
      "Coerce new vals appropriately")
  (is (= {:foo 1 :bar "1" :c {:a 2}}
         (sc/coerce ::merge {:foo 1 :bar "1" :c {:a 2}}))
      "Leave out ok vals")

  (is (= "garbage" (sc/coerce ::merge "garbage"))
      "garbage is passthrough"))

(def d :kw)
(defmulti multi #'d)
(defmethod multi :default [_] (s/keys :req-un [::foo]))
(defmethod multi :kw [_] ::unqualified)
(s/def ::multi (s/multi-spec multi :hit))

(deftest test-multi-spec
  (is (= {:not "foo"} (sc/coerce ::multi {:not "foo"})))
  (is (= {:foo 1} (sc/coerce ::multi {:foo 1})))
  (is (= {:foo 1} (sc/coerce ::multi {:foo "1"})))
  (is (= {:foo 1 :d :kw} (sc/coerce ::multi {:d :kw :foo "1"})))
  (is (= "garbage" (sc/coerce ::multi "garbage"))))
