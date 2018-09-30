# Change Log

## [1.0.0-??? in progress]
- `inst?` coercion now accepts a wider range of date & date/time patterns
- `spec-coerce.core/*inst-format*` is dynamic and can be rebound if you need more formats
- added `deps.edn` setup

## [1.0.0-alpha6]
- Support `s/keys`
- More tolerant specs for internals
- Support 1.9 special numbers (`##-Inf` `##Inf` `##NaN` `NaN` `Infinity` `-Infinity`)
- Use `decimal?` instead of `bigdec?` (Clojure 1.9)
- Thanks to `joodie` for these updates!

## [1.0.0-alpha5]
- Support overrides on `sc/coerce-structure`

## [1.0.0-alpha4]

- Fix NaN cases for number parsing on CLJS, now they are considered bad parsing, making the original value returns.
- In CLJS, string "NaN" is coerced to `js/NaN`.

## [1.0.0-alpha3]

- Support `s/coll-of` predicate.
- Support `s/map-of` predicate.
- Support `s/or` predicate.

## [1.0.0-alpha2]

- Initial release.
