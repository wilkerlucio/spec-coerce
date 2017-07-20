(ns spec-coerce.core
  (:refer-clojure :exclude [def])
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            #?(:clj [clojure.instant]))
  #?(:clj (:import (java.util UUID))))

(s/def ::coerce-fn
  (s/fspec :args (s/cat :x string?) :ret any?))

(defonce ^:private registry-ref (atom {}))

(defn parse-long [x]
  #?(:clj (Long/parseLong x)
     :cljs (js/parseInt x)))

(defn parse-double [x]
  #?(:clj (Double/parseDouble x)
     :cljs (js/parseFloat x)))

(defn parse-uuid [x]
  #?(:clj (UUID/fromString x)
     :cljs (uuid x)))

(defn parse-inst [x]
  #?(:clj (clojure.instant/read-instant-timestamp x)
     :cljs (js/Date. x)))

(defmulti sym->coercer identity)

(defmethod sym->coercer `int? [_] parse-long)
(defmethod sym->coercer `nat-int? [_] parse-long)
(defmethod sym->coercer `pos-int? [_] parse-long)
(defmethod sym->coercer `neg-int? [_] parse-long)
(defmethod sym->coercer `inst? [_] parse-inst)
(defmethod sym->coercer `uuid? [_] parse-uuid)
(defmethod sym->coercer `keyword? [_] keyword)

#?(:clj (defmethod sym->coercer `bigdec? [_] #(bigdec %)))

(defmethod sym->coercer :default [_] identity)

(s/fdef sym->coercer
  :args (s/cat :sym symbol?)
  :ret ::coerce-fn)

(defn- safe-form [spec]
  (if (contains? (s/registry) spec)
    (s/form spec)))

(defn- form->spec [and-spec]
  (if (and (seq? and-spec)
           (= (first and-spec) `s/and))
    (second and-spec)
    and-spec))

(defn- accept-keyword [x]
  (if (qualified-keyword? x) x))

(defn spec->coerce-sym [spec]
  "Determine the main spec symbol from a spec form."
  (let [f (safe-form spec)]
    (let [spec-def (form->spec f)]
      (if (qualified-keyword? spec-def)
        (recur spec-def)
        spec-def))))

(defn infer-coercion [k]
  "Infer a coercer function from a given spec."
  (sym->coercer (spec->coerce-sym k)))

(s/fdef infer-coercion
  :args (s/cat :k qualified-keyword?)
  :ret ::coerce-fn)

(defn parent-coercer [k]
  "Look up for the parent coercer using the spec hierarchy."
  (or (-> (s/get-spec k) accept-keyword)
      (-> (form->spec (safe-form k)) accept-keyword)))

(s/fdef parent-coercer
  :args (s/cat :k qualified-keyword?)
  :ret (s/nilable ::coerce-fn))

(defn find-registry-coerce [k]
  (if-let [c (get @registry-ref k)]
    c
    (when-let [parent (-> (parent-coercer k) accept-keyword)]
      (recur parent))))

(defn coerce-fn [k]
  "Get the coercing function from a given key. First it tries to lookup the coercion
  on the registry, otherwise try to infer from the specs. In case nothing is found, identity function is returned."
  (or (find-registry-coerce k)
      (infer-coercion k)))

(s/fdef coerce-fn
  :args (s/cat :k qualified-keyword?)
  :ret ::coerce-fn)

(defn coerce [k x]
  "Coerce a value x using coercer k. This function will first try to use
  a coercer from the registry, otherwise it will try to infer a coercer from
  the spec with the same name. Coercion will only be tried if x is a string.
  Returns original value in case a coercer can't be found."
  (if-let [coerce-fn (coerce-fn k)]
    (if (string? x)
      (coerce-fn x)
      x)
    x))

(s/fdef coerce
  :args (s/cat :k qualified-keyword? :x any?)
  :ret any?)

(defn ^:skip-wiki def-impl [k coerce-fn]
  (assert (and (ident? k) (namespace k)) "k must be namespaced keyword")
  (swap! registry-ref assoc k coerce-fn)
  k)

(s/fdef def-impl
  :args (s/cat :k qualified-keyword?
               :coercion ::coerce-fn)
  :ret any?)

(defmacro def
  "Given a namespace-qualified keyword, and a coerce function, makes an entry in the
  registry mapping k to the coerce function."
  [k coercion]
  `(def-impl '~k ~coercion))

(s/fdef def
  :args (s/cat :k qualified-keyword?
               :coercion any?)
  :ret qualified-keyword?)

(defn coerce-structure [x]
  "Recursively coerce map values on a structure."
  (walk/prewalk (fn [x]
                  (if (map? x)
                    (with-meta (into {} (map (fn [[k v]] [k (coerce k v)])) x)
                               (meta x))
                    x))
                x))

(s/fdef coerce-structure
  :args (s/cat :x any?)
  :ret any?)
