(defproject spork "0.1.9.8-SNAPSHOT"
  :description
  "A set of libraries derived from Spoon's Operations Research Kit.
   Libraries are modular and are suppoorted as stand-alone dependencies.
   This project is an uberjar that includes all of the modular bits in a single
   jar, for easier dependencies.  spork is purpose-built to be clojure-centric,
   focusing on a functional API, a default towards purity and persistent data
   structures, with alternative mutable/imperative counterparts.  The vast
   majority of spork is written in Clojure, with an intentional nod toward
   independence from external libraries, particularly Java libs.  Consequently,
   spork should provide a lightweight, clojure-based platform for the topics
   covered by the libraries.  I am currently working to eliminate legacy
   java dependencies where possible."
  :url "None Currently"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure  "1.8.0"]
                 [clj-tuple            "0.2.2"]
                 [net.jpountz.lz4/lz4    "1.3"] ;lz4 compression test.
                 [net.sf.jung/jung-api           "2.0.1"]
                 [net.sf.jung/jung-graph-impl    "2.0.1"]
                 [net.sf.jung/jung-algorithms    "2.0.1"]
                 [net.sf.jung/jung-visualization "2.0.1"]
                 [net.sf.jung/jung-io "2.0.1"]
                 [org.apache.poi/poi    "3.9"]
		 [org.apache.poi/poi-ooxml "3.9"]

                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/data.avl   "0.0.13"]
                ; [iota "1.1.2"]
                 [org.clojure/core.rrb-vector  "0.0.11"]
                 [ctries.clj "0.0.3"]
                ;experimental dependencies, ephemeral.
                 
                ;[org.clojure/data.finger-tree "0.0.2"]
                ;[primitive-math         "0.1.3"]
                ;[org.clojure/core.match "0.2.1"]
                ;[immutable-int-map    "0.1.0"]
                
;                 [clojure-watch "LATEST"] ;ephemeral dependency.
                 ]
  :aot [spork.cljgui.components.PaintPanel]
  :profiles {:publish [:uberjar
                       {:aot [;spork.cljgui.components.PaintPanel
                              spork.cljgui.components.swing
                              spork.util.table]}]
             :install {:aot [;spork.cljgui.components.PaintPanel
                             spork.cljgui.components.swing
                             spork.util.table]}}
  :jvm-opts ^:replace ["-Xmx1g" "-XX:NewSize=200m"])
