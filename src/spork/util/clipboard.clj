;;TOM SPOON 9 July 2012
;;a clojure wrapper for working with the clipboard. 
;;REALLY useful for interactive work (i.e. munging data using excel for 
;;interop).

(ns spork.util.clipboard
  (:use [clojure.string :only [split-lines]])
  (:import [java.awt.datatransfer Clipboard DataFlavor Transferable 
                                  StringSelection]
           [java.awt Toolkit]))

;;this gets tricky if we don't call it on the EDT, gui toolkits
;;like Substance get upset and throw opaque null pointer errors.
(defn ^Clipboard get-clipboard []
  (try (.getSystemClipboard ^Toolkit (Toolkit/getDefaultToolkit))
       (catch Exception e (Clipboard. "headless"))))

(defn get-clipboard-text
  "Rips text from the clipboard, if it's text-able..."
  []
  (let [^Transferable t (.getContents (get-clipboard) nil)]
    (when (.isDataFlavorSupported t DataFlavor/stringFlavor)
      (str (.getTransferData t DataFlavor/stringFlavor)))))

(defn put-clipboard-text
  [s]
  (.setContents ^Clipboard (get-clipboard) (StringSelection. s) nil))

(defn ^String copy!
  "Returns the text currently on the clipboard.  Assumes data on clipboard
   is amenable to text."
  [] (get-clipboard-text))

(defn paste!
  "Pastes a string to the clipboard.  Applies str to s."
  [s] (put-clipboard-text (str s)))

(defmacro with-out-clipboard
  "Evaluates body in with-out-str context, pasting the resulting 
   string to the clipboard."
  [& body]
  `(let [res# (with-out-str ~@body)]
     (paste! res#)))

(defmacro with-in-clipboard
  "Evaluates body in with-out-str context, pasting the resulting 
   string to the clipboard."
  [& body]
  `(let [source# (copy!)]
     (with-in-str source# ~@body)))

(defn read-string-board
   "Applies read-string to evaluate text on the clipboard."
   []
  (read-string (with-in-clipboard (slurp *in*))))

(defn readline-board
  "Reads the first line of input from the clipboard."
  []
  (with-in-clipboard (read-line)))

(defn getlines-board
  "Splits clipboard text by lines."
  []
  (with-in-clipboard (split-lines (slurp *in*)))) 
