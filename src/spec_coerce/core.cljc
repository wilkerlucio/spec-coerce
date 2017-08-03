(ns spec-coerce.core
  (:refer-clojure :exclude [def])
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            [clojure.string :as str]
    #?(:clj
            [clojure.instant]))
  #?(:clj
     (:import (java.util UUID)
              (java.net URI))))

(declare coerce)

(s/def ::coerce-fn
  (s/fspec :args (s/cat :x string?) :ret any?))

(defonce ^:private registry-ref (atom {}))

(defn parse-long [x]
  (if (string? x)
    (try
      #?(:clj  (Long/parseLong x)
         :cljs (if (= "NaN" x)
                 js/NaN
                 (let [v (js/parseInt x)]
                  (if (js/isNaN v) x v))))
      (catch #?(:clj Exception :cljs :default) _
        x))
    x))

(defn parse-double [x]
  (if (string? x)
    (try
      #?(:clj  (Double/parseDouble x)
         :cljs (if (= "NaN" x)
                 js/NaN
                 (let [v (js/parseFloat x)]
                  (if (js/isNaN v) x v))))
      (catch #?(:clj Exception :cljs :default) _
        x))
    x))

(defn parse-uuid [x]
  (if (string? x)
    (try
      #?(:clj  (UUID/fromString x)
         :cljs (uuid x))
      (catch #?(:clj Exception :cljs :default) _
        x))
    x))

(defn parse-inst [x]
  (if (string? x)
    (try
      #?(:clj  (clojure.instant/read-instant-timestamp x)
         :cljs (js/Date. x))
      (catch #?(:clj Exception :cljs :default) _
        x))
    x))

(defn parse-boolean [x]
  (case x
    "true" true
    "false" false
    x))

(defn parse-keyword [x]
  (if (string? x)
    (if (str/starts-with? x ":")
      (keyword (subs x 1))
      (keyword x))
    x))

(defn parse-symbol [x]
  (if (string? x)
    (symbol x)
    x))

(defn parse-ident [x]
  (if (string? x)
    (if (str/starts-with? x ":")
      (parse-keyword x)
      (symbol x))
    x))

(defn parse-nil [x]
  (if (and (string? x)
           (#{"nil" "null"} (str/trim x)))
    nil
    x))

(defn parse-or [[_ & pairs]]
  (fn [x]
    (reduce
      (fn [x [_ pred]]
        (let [coerced (coerce pred x)]
          (if (= x coerced)
            x
            (reduced coerced))))
      x
      (partition 2 pairs))))

(defn parse-coll-of [[_ pred & _]]
  (fn [x]
    (if (sequential? x)
      (into (empty x) (map (partial coerce pred)) x)
      x)))

(defn parse-map-of [[_ kpred vpred & _]]
  (fn [x]
    (if (associative? x)
      (into {} (map (fn [[k v]]
                      [(coerce kpred k)
                       (coerce vpred v)]))
            x)
      x)))

#?(:clj
   (defn parse-bigdec [x]
     (if (string? x)
       (if (str/ends-with? x "M")
         (bigdec (subs x 0 (dec (count x))))
         (bigdec x))
       x)))

#?(:clj
   (defn parse-uri [x]
     (if (string? x)
       (URI. x)
       x)))

(defmulti sym->coercer
  (fn [x]
    (if (sequential? x)
      (first x)
      x)))

(defmethod sym->coercer `number? [_] parse-double)
(defmethod sym->coercer `integer? [_] parse-long)
(defmethod sym->coercer `int? [_] parse-long)
(defmethod sym->coercer `pos-int? [_] parse-long)
(defmethod sym->coercer `neg-int? [_] parse-long)
(defmethod sym->coercer `nat-int? [_] parse-long)
(defmethod sym->coercer `even? [_] parse-long)
(defmethod sym->coercer `odd? [_] parse-long)
(defmethod sym->coercer `float? [_] parse-double)
(defmethod sym->coercer `double? [_] parse-double)
(defmethod sym->coercer `boolean? [_] parse-boolean)
(defmethod sym->coercer `ident? [_] parse-ident)
(defmethod sym->coercer `simple-ident? [_] parse-ident)
(defmethod sym->coercer `qualified-ident? [_] parse-ident)
(defmethod sym->coercer `keyword? [_] parse-keyword)
(defmethod sym->coercer `simple-keyword? [_] parse-keyword)
(defmethod sym->coercer `qualified-keyword? [_] parse-keyword)
(defmethod sym->coercer `symbol? [_] parse-symbol)
(defmethod sym->coercer `simple-symbol? [_] parse-symbol)
(defmethod sym->coercer `qualified-symbol? [_] parse-symbol)
(defmethod sym->coercer `uuid? [_] parse-uuid)
(defmethod sym->coercer `inst? [_] parse-inst)
(defmethod sym->coercer `nil? [_] parse-nil)
(defmethod sym->coercer `false? [_] parse-boolean)
(defmethod sym->coercer `true? [_] parse-boolean)
(defmethod sym->coercer `zero? [_] parse-long)
(defmethod sym->coercer `s/or [form] (parse-or form))
(defmethod sym->coercer `s/coll-of [form] (parse-coll-of form))
(defmethod sym->coercer `s/map-of [form] (parse-map-of form))

#?(:clj (defmethod sym->coercer `uri? [_] parse-uri))
#?(:clj (defmethod sym->coercer `bigdec? [_] parse-bigdec))

(defmethod sym->coercer :default [_] identity)

(s/fdef sym->coercer
  :args (s/cat :sym symbol?)
  :ret ::coerce-fn)

(defn safe-form [spec]
  (if (contains? (s/registry) spec)
    (s/form spec)))

(defn form->spec [and-spec]
  (if (and (seq? and-spec)
           (= (first and-spec) `s/and))
    (second and-spec)
    and-spec))

(defn accept-keyword [x]
  (if (qualified-keyword? x) x))

(defn accept-symbol [x]
  (if (qualified-symbol? x) x))

(defn accept-symbol-call [spec]
  (if (and (seq? spec)
           (symbol? (first spec)))
    spec))

(defn spec->coerce-sym [spec]
  "Determine the main spec symbol from a spec form."
  (let [f (or (safe-form spec)
              (accept-symbol spec)
              (accept-symbol-call spec))]
    (let [spec-def (form->spec f)]
      (if (qualified-keyword? spec-def)
        (recur spec-def)
        spec-def))))

(defn infer-coercion [k]
  "Infer a coercer function from a given spec."
  (-> (spec->coerce-sym k)
      (sym->coercer)))

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
    (coerce-fn x)
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

(defn coerce-structure
  "Recursively coerce map values on a structure."
  ([x] (coerce-structure x {}))
  ([x {::keys [overrides]}]
    (walk/prewalk (fn [x]
                    (if (map? x)
                      (with-meta (into {} (map (fn [[k v]]
                                                 (let [coercion (get overrides k k)]
                                                   [k (coerce coercion v)]))) x)
                                 (meta x))
                      x))
                  x)))

(s/fdef coerce-structure
  :args (s/cat :x any?)
  :ret any?)
