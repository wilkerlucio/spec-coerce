(ns spec-coerce.core-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer :all]
            [spec-coerce.core :as sc]))

(s/def ::infer-int int?)
(s/def ::infer-nat-int? nat-int?)
(s/def ::infer-pos-int? pos-int?)
(s/def ::infer-neg-int neg-int?)
(s/def ::infer-bigdec? bigdec?)
(s/def ::infer-inst? inst?)
(s/def ::infer-uuid? uuid?)
(s/def ::infer-keyword? keyword?)
(s/def ::infer-and-spec (s/and int? #(> % 10)))
(s/def ::infer-and-spec-indirect (s/and ::infer-int #(> % 10)))

(sc/def ::some-coercion #(Long/parseLong %))

(s/def ::first-layer int?)
(sc/def ::first-layer #(-> (Long/parseLong %) inc))

(s/def ::second-layer ::first-layer)
(s/def ::second-layer-and (s/and ::first-layer #(> % 10)))

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))
