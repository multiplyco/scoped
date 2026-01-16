(ns co.multiply.scoped.helpers
  #?(:cljs (:require-macros co.multiply.scoped.helpers))
  #?(:clj (:import
            [java.util Map]
            [clojure.lang Associative Counted IDeref IEditableCollection ITransientAssociative ITransientCollection Indexed Var$Unbound])))


;; ## Helpers
;; ############################################################
(defmacro if-cljs
  "Helper for switching between ClojureScript and Clojure implementations in macros."
  [then else]
  (if (contains? &env '&env)
    ;; Inside another macro - emit a runtime check
    `(if (:ns ~'&env) ~then ~else)
    ;; Direct use - check now
    (if (:ns &env) then else)))


(defmacro ^:no-doc persistentAssoc
  [coll k v]
  (if-cljs
    `(cljs.core/-assoc ~coll ~k ~v)
    `(Associative/.assoc ~coll ~k ~v)))


(defmacro ^:no-doc asTransient
  [coll]
  (if-cljs
    `(cljs.core/-as-transient ~coll)
    `(IEditableCollection/.asTransient ~coll)))


(defmacro ^:no-doc transientAssoc
  [coll k v]
  (if-cljs
    `(cljs.core/-assoc! ~coll ~k ~v)
    `(ITransientAssociative/.assoc ~coll ~k ~v)))


(defmacro ^:no-doc asPersistent
  [coll]
  (if-cljs
    `(cljs.core/-persistent! ~coll)
    `(ITransientCollection/.persistent ~coll)))


(defmacro ^:no-doc vecCount
  [coll]
  (if-cljs
    `(cljs.core/-count ~coll)
    `(Counted/.count ~coll)))


(defmacro ^:no-doc vecNth
  [coll idx]
  (if-cljs
    `(cljs.core/-nth ~coll ~idx)
    `(Indexed/.nth ~coll ~idx)))


(defmacro ^:no-doc getOrDefault
  [m k default]
  (if-cljs
    `(cljs.core/-lookup ~m ~k ~default)
    `(Map/.getOrDefault ~m ~k ~default)))
