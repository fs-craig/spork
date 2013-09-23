;;A namespace for defining common neighborhood functions for solutions.
;;Flexing the ideas present in the presentation of generic solutions from
;;__spork.opt.representations__ , we can define general neighborhood functions
;;for any representation.
(ns spork.opt.neighborhood
  (:require [spork.opt  [representation :as rep]]
            [spork.util [vectors :as v] [stats :as stats]]))

;;Generic Neighborhoods
;;=====================

;;Given a solution representation, from spork.opt.representation/defsolution or
;;a compatible means, we would like to define ways to permute the solutions 
;;according to some strategy.  As our solution representations efficiently
;;map complex variable domains to an underlying normalized representation, we 
;;can define neighborhood functions as either manipulations of the normalized 
;;form, or as transforms of the "nicer" solution domain.


;;Randomly Sampled Neighborhoods
;;==============================

;;The simplest neighborhood is to range over the normalized form of the 
;;solution, in each dimension, using a uniform distribution.  The normal form 
;;is defined specifically to make this easy to do.  Since the solution takes 
;;care of encoding everything, any range-based constraints will enforced 
;;automatically.

(defn random-neighbor
  "Generates a random neighbor based on the solution's normalized encoding."
  [sol] 
  (rep/from-normal sol (rep/random-normal-vector (rep/basis-vector sol))))

(defn ^double square [^double x] (* x x))

(defn cauchy-vec
  "Given a vector, returns a random normal vector with each element drawn from 
   cauchy distribution, whose elements range between [0, 1]."  
  [v]
  (v/map-vec v (fn [_] (stats/rand-cauchy))))

;;Composite Neighborhoods
;;=======================
;;A more sophisticated way to generate neighborhoods using our normalized 
;;solution form, is to treat each element of the normal vector as a stochastic
;;variable drawn from a unique distribution.  This is identical to the default 
;;behavior above, where we effectively treat any n-element normalized vector 
;;as the scaled output of a random variate based on a neighborhood spec.
;;The neighborhood spec is a simple data structure that maps elements in the 
;;normal vector to distributions.  It also maintains any data for the 
;;distributions, which will help us mix arbitrary variables. 

(defn vec-by
  "Computes a new vector, based on the old vector v0.  Like map-indexed, 
   applies f to the current index, and the value in v0, storing the 
   corresponding values in a new vector."
  [v0 f]
  (let [bound (v/dimension v0)]
    (loop [acc (v/get-empty-vec bound)
           idx 0]
      (if (= idx bound) acc
          (recur (v/set-vec acc idx (f idx (v/vec-nth v0 idx)))
                 (unchecked-inc idx))))))             


;;Neighborhoods, at least numerically distributed, are just manipulations of 
;;normal vectors.  The solution encoding does the rest of the work for us, 
;;by projecting back into a domain that a cost function can apply to.
(defn cauchy-neighbor
  "Given an input solution, generates a random solution by perturbing the 
   normalized representation of the solution using a random vector generated by
   a cauchy distribution."
  [sol]
  (rep/from-normal sol (cauchy-vec (rep/basis-vector sol)))) 

;;Another general way to sample an n-dimensional space is to use some 
;;distribution.

;;Testing 
(comment 
(rep/defsolution one-d [x [0 50]])
(def simple-vec  (v/->vec1 0.0))
(defn cauchy-samples [n] 
  (into [] (take n (map #(v/vec-nth % 0) (repeatedly #(cauchy-vec simple-vec)))))) 
)

