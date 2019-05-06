(ns cells.cell
  (:require [cells.util :as util]
            [applied-science.js-interop :as j])
  (:refer-clojure :exclude [bound-fn assoc! read]))

(defn- read* [cell k not-found]
  `(let [cell# ~cell
         tx-cell# (when (some? ~'cells.cell/*tx-changes*)
                    (get @~'cells.cell/*tx-changes* cell#))]
     (j/get tx-cell# ~k
            (j/get-in cell# [~'.-state ~k] ~not-found))))

(defmacro ^:private read
  ([cell k]
   (read* cell k nil))
  ([cell k not-found]
   (read* cell k not-found)))

(defmacro ^:private assoc! [cell k v]
  `(~'cells.cell/write-cell! ~cell
    (~'applied-science.js-interop/obj ~k ~v)))

(defmacro ^:private update! [cell k f & args]
  `(let [cell# ~cell]
     (assoc! cell# ~k
             (~f (read cell# ~k) ~@args))))

(defmacro defcell
  "Defines a named cell."
  [the-name & body]
  (let [[docstring body] (if (string? (first body))
                           [(first body) (rest body)]
                           [nil body])
        [options body] (if (and (map? (first body)) (> (count body) 1))
                         [(first body) (rest body)]
                         [nil body])]
    `(do
       (declare ~the-name)
       (let [prev-cell# ~the-name]
         (def ~(with-meta the-name options)
           ~@(when docstring (list docstring))
           (~'cells.cell/cell*
            (fn [~'self] ~@body)
            {:def?      true
             :prev-cell prev-cell#}))))))

(defmacro cell
  "Returns an anonymous cell. Only one cell will be returned per lexical instance of `cell`,
  unless a unique `key` is provided. `self` is brought into scope, referring to the current cell."
  ([expr]
   `(~'cells.cell/cell nil ~expr))
  ([key expr]
   (let [id (util/unique-id)]
     `(~'cells.cell/cell*
       (fn [~'self] ~expr)
       {:memo-key (str ~id "#" (hash ~key))}))))

(defmacro bound-fn
  "Returns an anonymous function which will evaluate in the context of the current cell
   (useful for handling async-state)"
  [& body]
  `(let [cell# ~'cells.cell/*cell*
         error-handler# ~'cells.cell/*error-handler*]
     (fn [& args#]
       (binding [~'cells.cell/*cell* cell#
                 ~'cells.cell/*error-handler* error-handler#]
         (try (apply (fn ~@body) args#)
              (catch ~'js/Error e#
                (~'cells.cell/error! cell# e#)))))))
