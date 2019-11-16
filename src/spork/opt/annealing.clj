;;A namespace for defining simulated annealing stochastic optimization
;;strategies, using the optimization libraries in collective.core.

;;Status: Mostly implemented, but still Work in Progress since 2013.
(ns spork.opt.annealing
  (:require [spork.opt [core :as core :refer [with-solve]]]
            [spork.util [stats :as stats]
                        [ranges :as r]]            
            [spork.util.numerics :refer :all]))

;;__Simulated Annealing__ comes in various forms, but the one most
;;commonly taught or described is of the Boltzmann form, or Boltzmann
;;Annealing.  The major difference  between various schemes for
;;annealers is the so-called 'cooling schedule', in which a
;;temperature analogue is gradually decayed over annealing-time.
;;Since the temperature variable is a primary input for acceptance
;;criteria and neighborhood functions, annealing algorithms choose
;;functions that map decreases in temperature to increases in
;;penalties for non-improving solutions, or tighter acceptance
;;criteria, as well as a decrease in the exploration of the decision
;;space.
   
;;Dr. Lester Ingber provides an excellent synopsis of Simulated Annealing, 
;;including so-called Fast Annealing, which uses a Cauchy distribution 
;;for cooling, and the variant he implemented in 1989, originally
;;called 'Very Fast Simulated Re-Annealing', renamed to 'Adaptive
;;Simulated Annealing', or ASA.
  
;;Dr. Ingber provides a slew of elegant proofs about the statistical 
;;guarantees for an annealing algorithm to find the global minimum of
;;any arbitrary function.  Such guarantees are deeply tied to the
;;cooling schedule, which ultimately implies how 'fast' the algorithm
;;explores the state space.  Preserving the statistical guarantee of 
;;finding the global minimum is a  primary goal of Simulated
;;Annealing.  Unfortunately, the cooling schedules chosen can 
;;lead to significant run-times, particularly over large, complex
;;state spaces.  Much of the simulated-annealing research has been
;;focused on finding better cooling schedules to improve the
;;convergence rate, and thus decreased the run-time, of annealers.

;;As with all 'hard' problems, when practicality overrides theoretical
;;purity,  i.e. in the face of high-dimension state spaces, turning to
;;approximation to make the problem tractable is a rational choice.
;;When approximately-optimal solutions are acceptable, one can engage
;;in a form of Simulated Quenching, in which the cooling schedule is
;;accelerated.  Quenching prevents thorough exploration of the state
;;space, and cannot guarantee finding the global optimum, but provides
;;massive performance gains.  

;;Dr. Ingber's ASA provides a flexible mechanism for efficiently searching
;;high-dimensional state spaces with the statistical guarantees of
;;finding the global optima, with the convenience of parameterized
;;quenching for approximate techniques.  Additionally, ASA allows one
;;to, via ensemble versions of cooling schedules, acceptance
;;criteria, and generating (or neighborhood) functions, finely mould
;;the search criteria to the sensitivities in each dimension of the
;;decision component.  In short, the D-dimensional ASA search is
;;actually composed of D individual 1-dimensional ASA searches, which
;;will have different cooling schedules.  ASA goes a step further, by
;;incorporating the search information gathered from each component's
;;dimensional search, to adapt the annealing parameters for other
;;dimensions.  As "sensitive" component states are found, the
;;information is propogated by rescaling the temperatures in other
;;states, effectively 're-annealing' on-the-fly, in response to
;;exploration of the value  space.  Finally, and most fundamentally,
;;the cooling schedule and generating function for each dimensional
;;coordinate are not directly based on either Boltzmann or Cauchy
;;distributions, but a distribution that includes fatter tails to
;;facilitate additional exploration at each stage of annealing-time.
;;The result is an exponential speedup in cooling in each dimension,
;;and thus the overall search speed - even for non-approximate
;;searches, drastically improves.  Dr. Ingber's existing library for
;;ASA, freely available at www.ingber.com, provides a c-library with a
;;slew of options and tweaks for ASA.  This clojure library will
;;implement basic Simulated Annealing, Simulated Quenching, and the
;;core ASA routine.  As time permits, additional tuning options and
;;tweaks will be incorporated from Dr. Ingber's excellent library.

;;Boltzmann Annealing and Generic Simulated Annealing
;;====================================================


;;In the literature, simulated annealing schemes are usefully broken down 
;;into 3 important functions: 
;;(g x) - probability density for the state space, i.e. a neihborhood function.
;;(h (delta e1 e2)) - acceptance probability between values.
;;(T k) or (t k) - the cooling schedule.  The rate at which temperature decays.

;;Bolting onto our generic solver framework, we map 'g to neighbors, 
;;'h to accept?, and T to decay.  Defaults, derived from the literature on 
;;simulated annealing, fast annealing, very-fast simulated annealing, and 
;;adaptive simulated annealing follow.

;;Acceptance criteria for simulated annealing
;;-------------------------------------------

;;There are various schemes for acceptance criteria.  The canonical scheme uses
;;a Boltzmann distribution, along with a random uniform variate, to determine
;;if a new solution should be accepted.  Alternatively called the Metropolis 
;;criterion, from the original Metropolis algorithm (a simulated annealing 
;;variant for monte carlo integration).

(defn boltzmann-sample
  "Samples from the Boltzmann probability distribution.  oldcost is equivalent
   to the previous 'energy state', while newcost is the new candidate energy
   state.  They are named cost since we use cost functions as energy analogues.
   t is temperature.  As t decreases, the probability of accepting 'worse' 
   energy states - higher costs - decreases rapidly.  At higher temperatures, 
   the disparity between energy states has less influence on the probability.
   As t approaches infinity, the contribution of the exponential term decreases, 
   approach a p of 0.5."
  ([t delta]  (/ 1.0 (+ 1.0 (Math/pow E (/ delta t)))))
  ([t oldcost newcost]
    (/ 1.0 (+ 1.0 (Math/pow E (/ (- newcost oldcost) t))))))

(defn boltzmann-accept?
  "Use the Boltzmann energy equation to determine probability of accepting 
   a poorer solution at a given temperature t.  Better solutions are accepted
   with P = 1.0, while poorer solutions are accepted with P = e^(-delta / t)"
  [t oldcost newcost]
  (try
    (let [delta (- oldcost newcost)]
          (if (pos? delta) true 
              (<= (rand) (boltzmann-sample t (* -1.0 delta)))))
    (catch Exception e (throw (Exception. (str {:t t :oldcost oldcost :newcost newcost}))))))
  
;;Decay Functions (Cooling Schedules) for Simulated Annealing 
;;===========================================================
;;Cooling schedules are crucial to the performance and correctness of
;;any simulated annealing search.  We provide several schedules
;;examined in the literature.  For interfacing with the generic search
;;protocols in collective.core, we implement cooling schedules via the
;;decay function in the ISolveable protocol.

;;Each of the following cooling schedules assumes k ranges from 0 to inf+.
;;In some cases, k is padded by 1 to ensure that the logarithmic functions 
;;are within their domain.  The net effect for each schedule is a monotonically
;;decreasing set of values, starting from the initial temperature t0, 
;;approaching 0.  The annealing parameters will allow callers to define the 
;;machine precision for temperatures "close enough" to 0.

(defn boltzmann-decay 
  "The original cooling schedule for Simulated Annealing.  Here for comparison.
   Slow!"
  [t0 k] (/ t0 (+ 1.0 (Math/log (+ 1.0 k)))))

(defn cauchy-decay 
  "The cooling schedule used in Fast Annealing.  Exponentially faster than the 
   classic cooling schedule used in conjunction with the boltzmann-based  
   generating function."
  [t0 k] (/ t0 (+ 1.0 k)))

(defn exponential-decay
  "Standard decay function for simulated quenching.  Each temperature decay 
   is proportional to the decay rate.  The temperature at step k is computed 
   analytically via an exponential decay, starting from temperature t0. 
   While simple, and fast, cooling in this fashion guarantees that we will 
   NOT maintain the ability to explore the entire state space."
  [decay-rate t0 k]
  (assert (and (> decay-rate 0) (<= decay-rate 1)) "decay-rate outside (0,1]")
  (* t0 (Math/pow E (* (- decay-rate  1.0) k))))

(defn geometric-decay 
  "Standard decay function for simulated quenching.  Each temperature decay 
   is proportional to the decay rate.  Yields a function that decays the 
   current temperature at each step."
  [decay-rate] 
  (assert (and (> decay-rate 0) (<= decay-rate 1)) 
          "decay-rate must be between (0,1]")
  (fn [t & args] (* decay-rate t)))

(defn asa-decay 
  "The cooling schedule defined by Dr. Lester Ingber's Adaptive Simulated
   Annealing.  Parameterized to allow a constant, c, to speed up or slow down
   the schedule, as well as a quenching factor, q, which when <> 1, will 
   speed or slow the schedule as well.  ASA typically assumes a unique schedule
   for each dimension of the solution representation, to allow further 
   fine-grained parameterization and adaptive search techniques.  The cooling 
   schedule may be by itself, for a 1-dimensional solution, or it may be part 
   of an ensemble cooling schedule, with different temperatures for each 
   component.  c and quench are effectively tuning parameters."
  ([c quench dimensions t0 k]  
   (* t0 (Math/pow E (* -1.0 c (Math/pow k (/ quench dimensions))))))
  ([dimensions t0 k] (asa-decay 1.0 1.0 dimensions t0 k))
  ([t0 k] (asa-decay 1.0 t0 k)))

;;Useful Generating (Neighborhood) Functions 
;;==========================================

;;Generating functions in simulated annealing typically map a coordinate, 
;;normalized to unit space, to a coordinate in the solution space.  If the 
;;solution has multiple dimensions [like a vector], then the generated 
;;coordinate will likewise be multi-dimensional.  The literature proposes a 
;;couple of generic ways to sample from continuous generating functions, and 
;;scale the sample to the solution space - whether the solution is continuous 
;;or not.  Even if the solution is continuous, we still have to discretize the
;;machine representation, so it's natural to extend said continuous mappings to 
;;discrete representations.  That means we can use just about any generic 
;;probability density function as a generating function for our neighborhood.

;;This technique is so useful, in fact, that an entire branch of spork is 
;;dedicated to specification of solution encodings in a normalized vector 
;;represenation.  __spork.opt.representation__ and __spork.opt.neighborhood__
;;provide functions and macros for defining solution representations in high 
;;level domains, while handling all of the normalized vector encoding and 
;;decoding processes for us.  We will extend their functionality here, to 
;;implement various annealing strategies.


;;Deviation Probability Densities 
;;===============================
;;There are a couple of generating functions used in the literature.
;;Usually named "g", these generating functions are designed to assign a 
;;probabilty to reaching a particular state from an existing state.

;;The boltzmann distribution is a poor neighborhood function in practice.
;;Working on implementing the Gaussian form of this guy later.
(comment 
(defn- boltzmann-neighbor 
  ([dimensions t old-x new-x]
    (let [delta (- new-x old-x)
          mult  (Math/pow (* 2.0 Math/PI) (/ dimensions -2.0))]
      (* (/ 1.0 (+ (1.0 (Math/pow E delta)))))))
  ([t old-x new-x] (boltzmann-neighbor 1.0 t old-x new-x)))
)


;;Fast Annealing uses a Cauchy distribution, which for d-dimensional 
;;spaces, is an ensemble of D 1-dimensional Cauchy distributions, which
;;are actually identical. We can use our Cauchy vector, with a temperature 
;;parameter that alters the scale.

;;Still working on this guy.  Doesn't matter much, since I'm using ASA 
;;primarily.
(comment 
(defn fast-annealing-pdf [temp d x]
  (/ temp 
     (Math/pow (+ (* x x) (* temp temp))
               (/ (+ d 1) 2))))
)


;;Adaptive Simulated Annealing Generating Function 
;;================================================

;;ASA uses a custom distribution, defined by Dr. Ingber, to provide a parametric
;;search over the dimensions of a state space that a) maintains so-called 
;;statistical guarantee (i.e. does not preclude the possibility) of finding the 
;;global optimum, and b) allows for parameterization across different 
;;temperatures.  Essentially, Dr. Ingber's generating function allows the 
;;annealer to dynamically adapt the sensitivity of the search in each dimension, 
;;and provides a convenient mechanism for both re-annealing, and quenching.

(defn asa-dist
  "Given a uniform random variate, uv, and a temperature parameter, 
   samples returns a value between [-1 1].  As temperature decreases, the 
   values pack really tightly around 0.0, although, like the Cauchy distribution
   we may still see seemingly large, but infrequent, jumps across the parameter
   space."
  ([temp y]
    (let [dir  (Math/signum (- y 0.5))]
      (* dir 
         temp 
         (- (Math/pow (+ 1.0 (/ 1.0 temp)) (Math/abs (- (* 2.0 y) 1.0)))
            1.0))))
  ([temp] (asa-dist temp (rand))))

;;The asa-dist is used to build a function that walks across a parameter range.
;;This can be any range of values, but since we already have a normalized 
;;representation, the step function will wander over the range [0 1].
;;__TODO__ See if we can generalize this to use any distribution.
;;__TODO__ We're sampling and tossing out draws that exceed constraints.
;;The upside is that we're probably unlikely to exceed constraints for long, 
;;the downside is we're wasting draws.  Is there a way to not waste draws? 
(defn asa-stepper 
  "Given a numeric range, defined by lower and upper, returns a function 
   that performs steps across the range, yielding values between [lower upper] .
   Uses a temperature parameter, temp, and the value of a previous value, 
   x0, to compute x."
  [lower upper & {:keys [step-func] :or {step-func asa-dist}}]
  (let [width (- lower upper)
        between? (fn between? [^double x] (and (>= x lower) (<= x upper)))
        take-step!   (^double fn take-step! [^double temp] (* (step-func temp) width))]
    (^double fn step!  [^double temp ^double x0]
      (loop [step (+ x0 (take-step! temp))
             n    0]
        (if (between? step) step
            (if (> n 30) 
              (throw (Exception. "Problem in asa-stepper, too many reps."))
              (recur (+ x0 (take-step! temp)) (unchecked-inc n))))))))

(defn midpoint [x y] 
  (+ x (/ (-  y x) 2.0)))


;;Reannealing
;;===========
;;Re-annealing is useful to spread out the search over the parameter space 
;;evenly.  Implementation pending.



;;Simulated Annealing Parameters
;;==============================
;;In simulated annealing, we use a temperature analogue, temp, to 
;;provide a measure of how 'hot' the ambient annealing process is, 
;;to allow our analagous search particles to move and collide at a 
;;higher velocity, this encouraging more chaos, and more exploration 
;;of the search space.  As the search proceeds, the decay function is 
;;applied to the temperature 
(defrecord sa-params 
    [decay-function temp tmin itermax equilibration accept?])

;;Simulated annealing just overrides the default acceptance criterion,
;;and uses the Boltzmann energy equation to bias the search toward
;;exploration early, and exploitation as the search proceeds.
;;__blank-sa-params__ provides a sane set of defaults for simulated
;;annealing, and will garantee that a solve will terminate.
(def blank-sa-params 
  (->sa-params 
    (geometric-decay 0.9) 
    1000000 0.00000001 1000 1
    (fn [incumbent candidate env]
      (let [t            (get (core/solve-state env) :t)
            old-cost     (core/candidate-cost incumbent)
            new-cost     (core/candidate-cost candidate)]
        (boltzmann-accept? t old-cost new-cost)))))

;;ASA requires us to keep track of a vector of temperatures, and a vector of 
;;cooling schedules for each parameter.  This allows us to re-scale the 
;;temperature
;(defrecord asa-params [

;;Basic Annealers 
;;===============

;;A wrapper for solve that builds the annealing environment for us.
;;We're basically just adding an extra map of parameters, derived from 
;;__blank-sa-params__ by default, which overload the default
;;stochastic optimization parameters, namely the acceptance criterion,
;;and adds new annealing-specific parameters.
(core/defsolver anneal
  {:keys [decay-function temp tmin itermax equilibration accept?] :as opts}
  (merge blank-sa-params  
         opts 
         {:t (or temp (get blank-sa-params :temp))}))

(defn asa-neighbor [bounds]
  (let [steps (vec (for [[l u] bounds]
                     (asa-stepper l u)))]
    (core/parametric-neighbor 
     (fn [vs env]
       (let [t (-> (core/solve-state env) :t)]
         (loop [ks  vs 
                remaining steps
                acc []]
           (if (empty? remaining) [acc] 
               (recur (rest ks)
                      (rest remaining)
                      (conj acc ((first remaining) t (first ks)))))))))))
;;this is a really crappy way to go, will implement a more performant version 
;;later...
(defn simple-anneal [vars cost-func]  
  (let [binds (partition 2 vars)
        xs    (vec (map first binds))]
    (anneal xs cost-func (asa-neighbor (map second binds)) {})))

(comment ;;testing
  ;; (def prob (simple-anneal [0 [0 100]
  ;;                           1 [0 100]]
  ;;                          (fn [xs]
  ;;                            (- (reduce + xs
  ;; (doseq [i (range 10)] (println ((juxt core/solve-optimum core/solve-best ) (core/solve prob))))

  )

(comment 
;;test annealable...make a random number generator, where cost is number 
;;n squared, neighbors are just a random draw in a range - if the number 
;;is drawn, we add 1 to it - , continue is a standard continuation function.
;;annealing this should push the number down to the bottom of the random 
;;number range/
(defn rand-annealable [somenum]
  (let [halfwidth (/ somenum 2 )]
    (make-annealable 
      (inc (rand-int somenum)) ;initial solution
      (fn [n] (* n n)) ;cost function, higher ints cost more
      (fn [n] (let [res (- (rand-int somenum) halfwidth)]  ;neighborhood fn
                (if (not= res n) res
                  (let [adjusted (inc res)]
                    (if (< adjusted halfwidth) 
                      adjusted 
                      (- adjusted halfwidth)))))))))
;test 
(defn solutions [] 
  (->> (anneal-seq (rand-annealable 1000))
       (map (juxt (comp get-solution :bestsol) :bestcost))
       (take 100)))
)

(comment ;testing 
  
(defn samples [lower upper & {:keys [t0] :or {t0 1000000}}]
  (let [stepper (asa-stepper lower upper)]
    (iterate (fn [[t k x0 n]] 
               (let [x (stepper t x0)]
                 (if (zero? (mod n 10.0))
                   [(asa-decay t0 (inc k)) (inc k)  x (inc n)]
                   [(asa-decay t0 k) k x (inc n)])))
             [t0 0 (midpoint lower upper) 0])))

(def simple-samples (samples 10 20))
(def uni-samples    (samples 0.0 1.0))
(def problem        (simple-anneal [0 [0 100]] 
                         (fn [xs] (abs (- (first xs) 50)))))
)        

;;misread 
(comment  
(defn asa-dist2
  "Given a uniform random variate, uv, and a temperature parameter, 
   samples returns a value between [-1 1].  As temperature decreases, the 
   values pack really tightly around 0.0, although, like the Cauchy distribution
   we may still see seemingly large, but infrequent, jumps across the parameter
   space."
  ([temp ^double y]
    (/ 1.0 
       (* 2.0 
          (+ (Math/abs y) temp)
          (ln (+ 1.0 (/ 1.0 temp))))))
  ([temp] (asa-dist2 (rand))))
)
