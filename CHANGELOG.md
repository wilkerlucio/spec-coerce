# Change Log

## [1.0.0-alpha16]
- Refer exclude new things from Clojure 1.11: `parse-boolean` `parse-double` `parse-long` `parse-uuid`

## [1.0.0-alpha15]
- Add support for `s/merge`
- Add support for `s/tuple`
- Add support for `s/multi-spec`

## [1.0.0-alpha14]
- Handle number->number, any->string, and symbol->keyword
- Infer coercion from specs given as sets

## [1.0.0-alpha13]
- Fix coercion of nilables

## [1.0.0-alpha12]
- Fix coercion of lists getting out of order

## [1.0.0-alpha11]
- Fix parsing of conditionals in unqualified keys

## [1.0.0-alpha10]
- Allow overriding coercers in the registry in a local context

## [1.0.0-alpha9]
- Coerce to bigdecimal from other number types

## [1.0.0-alpha8]
- Return original value when trying to `coerce!` on simple keywords

## [1.0.0-alpha7]
- `inst?` coercion now accepts a wider range of date & date/time patterns
- `spec-coerce.core/*inst-format*` is dynamic and can be rebound if you need more formats
- added `deps.edn` setup
- support nilable
- add `sc/coerce!`
- add `sc/conform`
- `sc/coerce-structure` supports `::sc/op` argument to use custom processor (eg: the new `sc/coerce!` or `sc/conform`)

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
