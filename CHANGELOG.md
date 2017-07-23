# Change Log

## [1.0.0-alpha4]

- Fix NaN cases for number parsing on CLJS, now they are considered bad parsing, making the original value returns.
- In CLJS, string "NaN" is coerced to `js/NaN`.

## [1.0.0-alpha3]

- Support `s/coll-of` predicate. 
- Support `s/map-of` predicate. 
- Support `s/or` predicate. 

## [1.0.0-alpha2]

- Initial release.
