;A generic set of operations for things that can be logged to.
(ns spork.util.log)

(defprotocol ILog 
  (write-log! [l s])
  (clear-log! [l])
  (close-log! [l]))
