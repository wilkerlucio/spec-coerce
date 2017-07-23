# spec-coerce

A Clojure(script) library designed to leverage your specs to coerce your information into correct types.

## Latest version

```
[spec-coerce "1.0.0-alpha2"]
```

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

; If you wanna play around or use a specific coercion, you can pass the predicate symbol directly
(sc/coerce `int? "40"); => 40

; To leverage map keys and coerce a composed structure, use coerce-structure
(sc/coerce-structure {::number      "42"
                      ::not-defined "bla"
                      :sub          {::odd-number "45"}})
; => {::number      42
;     ::not-defined "bla"
;     :sub          {::odd-number 45}}

; If you want to set a custom coercer for a given spec, use the spec-coerce registry
(defrecord SomeClass [x])
(s/def ::my-custom-attr #(instance? SomeClass %))
(sc/def ::my-custom-attr #(map->SomeClass {:x %}))

; Custom registered keywords always takes precedence over inference
(sc/coerce ::my-custom-attr "Z") ; => #user.SomeClass{:x "Z"}
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

; Others
(sc/coerce `uuid? "d6e73cc5-95bc-496a-951c-87f11af0d839")   ; => #uuid "d6e73cc5-95bc-496a-951c-87f11af0d839"
(sc/coerce `inst? "2017-07-21")                             ; => #inst "2017-07-21T00:00:00.000000000-00:00"
(sc/coerce `nil? "nil")                                     ; => nil
(sc/coerce `nil? "null")                                    ; => nil

;; Clojure only:
(sc/coerce `uri? "http://site.com") ; => (URI. "http://site.com")
(sc/coerce `bigdec? "42.42") ; => 42.42M
(sc/coerce `bigdec? "42.42M") ; => 42.42M
```

### Adding new predicates

The conversion from symbol to parser is a open multi-method, you can extend it
by adding new methods with the qualified symbol of the predicate:

```clojure
(defmethod sc/sym->coercer [_]
  )
```

## Next Features

Here is a list of features on track for implementation:

* Support `coll-of` to coerce sequences
* Support `map-of` to coerce homogeneos maps
* Support `s/or`, trying each option
* Coercion overrides map to specify contextual coercions

## License

Copyright © 2017 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
