(ns co.multiply.scoped
  (:require-macros co.multiply.scoped)
  (:require [co.multiply.scoped.helpers :as h]
    [co.multiply.scoped.impl]))


(def skip
  "Sentinel for omitting a binding; preserves its inherited value or absence."
  h/skip)
