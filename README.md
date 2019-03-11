# spec-coerce [![Clojars Project](https://img.shields.io/clojars/v/spec-coerce.svg)](https://clojars.org/spec-coerce) [![cljdoc badge](https://cljdoc.xyz/badge/spec-coerce/spec-coerce)](https://cljdoc.xyz/d/spec-coerce/spec-coerce/CURRENT) [![CircleCI](https://circleci.com/gh/wilkerlucio/spec-coerce.svg?style=svg)](https://circleci.com/gh/wilkerlucio/spec-coerce) 

A Clojure(script) library designed to leverage your specs to coerce your information into correct types.

Spec Coerce will remain in alpha while clojure.spec still in alpha.

## Usage

Learn by example:

```clojure
(ns spec-coerce.example
  (:require
    [clojure.spec.alpha :as s]
    [spec-coerce.core :as sc]))
    
; Define a spec as usual
(s/def ::number int?)

; Call the coerce method passing the spec and the value to be coerced
(sc/coerce ::number "42") ; => 42

; Like spec generators, when using `and` it will use the first item as the inference source
(s/def ::odd-number (s/and int? odd?))
(sc/coerce ::odd-number "5") ; => 5

; When inferring the coercion, it tries to resolve the upmost spec in the definition
(s/def ::extended (s/and ::odd-number #(> % 10)))
(sc/coerce ::extended "11") ; => 11

; Nilables are considered
(s/def ::nilable (s/nilable ::number))
(sc/coerce ::nilable "42") ; => 42
(sc/coerce ::nilable "nil") ; => nil
(sc/coerce ::nilable "foo") ; => "foo"

; If you wanna play around or use a specific coercion, you can pass the predicate symbol directly
(sc/coerce `int? "40") ; => 40

; Parsers are written to be safe to call, when unable to coerce they will return the original value
(sc/coerce `int? "40.2") ; => "40.2" 
(sc/coerce `inst? "date") ; => "date" 

; To leverage map keys and coerce a composed structure, use coerce-structure
(sc/coerce-structure {::number      "42"
                      ::not-defined "bla"
                      :sub          {::odd-number "45"}})
; => {::number      42
;     ::not-defined "bla"
;     :sub          {::odd-number 45}}

; coerce-structure supports overrides, so you can set a custom coercer for a specific context, and can be also a point
; to set coercer for unqualified keys
(sc/coerce-structure {::number      "42"
                      ::not-defined "bla"
                      :unqualified  "12"
                      :sub          {::odd-number "45"}}
                     {::sc/overrides {::not-defined `keyword?
                                      :unqualified  ::number}})
; => {::number      42
;     ::not-defined :bla
;     :unqualified  12
;     :sub          {::odd-number 45}}

; If you want to set a custom coercer for a given spec, use the spec-coerce registry
(defrecord SomeClass [x])
(s/def ::my-custom-attr #(instance? SomeClass %))
(sc/def ::my-custom-attr #(map->SomeClass {:x %}))

; Custom registered keywords always takes precedence over inference
(sc/coerce ::my-custom-attr "Z") ; => #user.SomeClass{:x "Z"}

; Coercers in the registry can be overriden within a specific context
(binding [sc/*overrides* {::my-custom-attr keyword}]
  (sc/coerce ::my-custom-attr "Z")) ; => :Z
```

Examples from predicate to coerced value:

```clojure
; Numbers
(sc/coerce `number? "42")                                   ; => 42.0
(sc/coerce `integer? "42")                                  ; => 42
(sc/coerce `int? "42")                                      ; => 42
(sc/coerce `pos-int? "42")                                  ; => 42
(sc/coerce `neg-int? "-42")                                 ; => -42
(sc/coerce `nat-int? "10")                                  ; => 10
(sc/coerce `even? "10")                                     ; => 10
(sc/coerce `odd? "9")                                       ; => 9
(sc/coerce `float? "42.42")                                 ; => 42.42
(sc/coerce `double? "42.42")                                ; => 42.42
(sc/coerce `zero? "0")                                      ; => 0

; Numbers on CLJS
(sc/coerce `int? "NaN")                                     ; => js/NaN
(sc/coerce `double? "NaN")                                  ; => js/NaN

; Booleans
(sc/coerce `boolean? "true")                                ; => true
(sc/coerce `boolean? "false")                               ; => false
(sc/coerce `true? "true")                                   ; => true
(sc/coerce `false? "false")                                 ; => false

; Idents
(sc/coerce `ident? ":foo/bar")                              ; => :foo/bar
(sc/coerce `ident? "foo/bar")                               ; => 'foo/bar
(sc/coerce `simple-ident? ":foo")                           ; => :foo
(sc/coerce `qualified-ident? ":foo/baz")                    ; => :foo/baz
(sc/coerce `keyword? "keyword")                             ; => :keyword
(sc/coerce `keyword? ":keyword")                            ; => :keyword
(sc/coerce `simple-keyword? ":simple-keyword")              ; => :simple-keyword
(sc/coerce `qualified-keyword? ":qualified/keyword")        ; => :qualified/keyword
(sc/coerce `symbol? "sym")                                  ; => 'sym
(sc/coerce `simple-symbol? "simple-sym")                    ; => 'simple-sym
(sc/coerce `qualified-symbol? "qualified/sym")              ; => 'qualified/sym

; Collections
(sc/coerce `(s/coll-of int?) ["5" "11" "42"])               ; => [5 11 42]
(sc/coerce `(s/coll-of int?) ["5" "11.3" "42"])             ; => [5 "11.3" 42]
(sc/coerce `(s/map-of keyword? int?) {"foo" "42" "bar" "31"})
; => {:foo 42 :bar 31}

; Branching
; tests are realized in order
(sc/coerce `(s/or :int int? :bool boolean?) "40")           ; 40
(sc/coerce `(s/or :int int? :bool boolean?) "true")         ; true
; returns original value when no options can handle
(sc/coerce `(s/or :int int? :bool boolean?) "nil")          ; "nil"

; Others
(sc/coerce `uuid? "d6e73cc5-95bc-496a-951c-87f11af0d839")   ; => #uuid "d6e73cc5-95bc-496a-951c-87f11af0d839"
(sc/coerce `inst? "2017-07-21")                             ; => #inst "2017-07-21T00:00:00.000000000-00:00"
(sc/coerce `nil? "nil")                                     ; => nil
(sc/coerce `nil? "null")                                    ; => nil

;; Clojure only:
(sc/coerce `uri? "http://site.com") ; => (URI. "http://site.com")
(sc/coerce `decimal? "42.42") ; => 42.42M
(sc/coerce `decimal? "42.42M") ; => 42.42M

;; Throw exception when coercion fails
(sc/coerce! `int? "abc") ; => throws (ex-info "Failed to coerce value" {:spec `int? :value "abc"})
(sc/coerce! :simple-keyword "abc") ; => "abc", coerce! doesn't do anything on simple keywords

;; Conform the result after coerce
(sc/conform `(s/or :int int? :bool boolean?) "40")          ; [:int 40]

;; Throw on coerce structure
(sc/coerce-structure {::number "42"} {::sc/op sc/coerce!})

;; Conform on coerce structure
(sc/coerce-structure {::number "42"} {::sc/op sc/conform})
```

## License

Copyright © 2017 Wilker Lúcio

Distributed under the MIT License.
