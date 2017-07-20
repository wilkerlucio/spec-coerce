(ns spec-coerce.cljs-test-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [spec-coerce.core-test]))

(doo-tests 'spec-coerce.core-test)
