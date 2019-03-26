(ns janus.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [janus.route-test]))

(doo-tests 'janus.route-test)
