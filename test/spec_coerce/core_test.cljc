(ns spec-coerce.core-test
  #?(:cljs (:require-macros [cljs.test :refer [deftest testing is are run-tests]]))
  (:require [clojure.spec.alpha :as s]
            #?(:clj [clojure.test :refer [deftest testing is are]])
            [spec-coerce.core :as sc]))

(s/def ::infer-int int?)
(s/def ::infer-nat-int? nat-int?)
(s/def ::infer-pos-int? pos-int?)
(s/def ::infer-neg-int neg-int?)
#?(:clj (s/def ::infer-bigdec? bigdec?))
(s/def ::infer-inst? inst?)
(s/def ::infer-uuid? uuid?)
(s/def ::infer-keyword? keyword?)
(s/def ::infer-and-spec (s/and int? #(> % 10)))
(s/def ::infer-and-spec-indirect (s/and ::infer-int #(> % 10)))

(sc/def ::some-coercion sc/parse-long)

(s/def ::first-layer int?)
(sc/def ::first-layer #(-> (sc/parse-long %) inc))

(s/def ::second-layer ::first-layer)
(s/def ::second-layer-and (s/and ::first-layer #(> % 10)))

(deftest coerce-from-registry
  (testing "it uses the registry to coerce a key"
    (is (= (sc/coerce ::some-coercion "123") 123)))

  (testing "it returns original value when it a coercion can't be found"
    (is (= (sc/coerce ::not-defined "123") "123"))))

(deftest coerce-inference-test
  (are [keyword input output] (= (sc/coerce keyword input) output)
    ::infer-int "123" 123
    ::infer-nat-int? "0" 0
    ::infer-pos-int? "44" 44
    ::infer-neg-int "-42" -42
    #?@(:clj [::infer-bigdec? "123.4" 123.4M])
    ;::infer-inst? "2017-05-03T10:40:00" #inst "2017-05-03T10:40:00"
    ::infer-uuid? "d6e73cc5-95bc-496a-951c-87f11af0d839" #uuid "d6e73cc5-95bc-496a-951c-87f11af0d839"
    ::infer-keyword? "foo" :foo
    ::infer-and-spec "42" 42
    ::infer-and-spec-indirect "43" 43
    ::second-layer "41" 42
    ::second-layer-and "41" 42))

(deftest test-coerce-structure
  (is (= (sc/coerce-structure {::some-coercion "321"
                               ::not-defined "bla"
                               :sub {::infer-int "42"}})
         {::some-coercion 321
          ::not-defined "bla"
          :sub {::infer-int 42}})))
