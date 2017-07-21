# spec-coerce

A Clojure library designed to leverage your specs to coerce string information into correct types.

## Latest version

```
[spec-coerce "0.1.0-SNAPSHOT"]
```

## Usage

Spec coerce is a library that leverages spec information to coerce values from strings to their specification types.

Examples

```clojure
(ns spec-coerce.example
  (:require
    [clojure.spec.alpha :as s]
    [spec-coerce.core :as sc]))
    
(s/def ::number int?)

(sc/coerce ::number "3") ; => 3

```

## License

Copyright © 2017 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
