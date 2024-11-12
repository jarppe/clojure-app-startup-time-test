(ns build
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.build.api :as b]
            [clojure.java.process :as p]))


(def src "./src/main")
(def target "./target")
(def classes (str target "/classes"))
(def compile-ns 'p.main)
(def main 'p.Main)

(def leyden-java "./leyden/bin/java")
(def native-image "./graalvm/bin/native-image")


(defn generate-code [_basis]
  (b/delete {:path src})
  (.mkdir (io/file src))
  (.mkdir (io/file src "p"))
  ; Generate 100 namespaces with 1000 functions in each:
  (let [ns-count 200
        fn-count 400
        ns-ids   (mapv (partial format "p%03d")
                       (range ns-count))
        fn-ids   (mapv (partial format "f%03d")
                       (range fn-count))]
    (println "build: generating test code: generating" ns-count "namespaces with each having" fn-count "functions...")
    (doseq [ns-id ns-ids]
      (spit (io/file src "p" (str ns-id ".clj"))
            (with-out-str
              (println (str "(ns p." ns-id ")"))
              (println)
              (doseq [fn-id fn-ids]
                (println "(defn" fn-id "^long [^long v] (inc v))"))
              (println)
              (println "(defn run ^long [^long v]")
              (print   "  (-> v")
              (doseq [fn-id fn-ids]
                (print (str "\n      (" fn-id ")")))
              (println "))")
              (println))))
    ; Generate main namespace to call above namespaces:
    (spit (io/file src "p" "main.clj")
          (with-out-str
            (println "(ns p.main")
            (println "  (:gen-class :name p.Main)")
            (print   "  (:require ")
            (print (str "p." (first ns-ids)))
            (doseq [ns-id (rest ns-ids)]
              (print (str "\n            p." ns-id)))
            (println "))")
            (println)
            (println "(defn run ^long [^long v]")
            (print   "  (-> v")
            (doseq [ns-id ns-ids]
              (print (str "\n      (p." ns-id "/run)")))
            (println "))")
            (println)
            (println "(defn -main [& _args]")
            (println "  (println \"ready\" (run 0))")
            (println "  (System/exit 0))")
            (println)))))


(defn compile-clj [basis]
  (println "build: compiling...")
  (b/compile-clj {:basis        basis
                  :ns-compile   [compile-ns]
                  :class-dir    classes
                  :compile-opts {:elide-meta     [:doc :file :line :added]
                                 :direct-linking true}
                  :bindings     {#'clojure.core/*assert* false}}))


(defn make-src-jar [basis]
  (println "build: making src jar...")
  (b/jar {:basis     basis
          :class-dir src
          :jar-file  (str target "/src.jar")}))


(defn make-aot-jar [basis]
  (println "build: making aot jar...")
  (b/jar {:basis     basis
          :main      main
          :class-dir classes
          :jar-file  (str target "/aot.jar")}))


(defn make-uber-jar [basis]
  (println "build: making über jar...")
  (b/uber {:basis     basis
           :main      main
           :class-dir classes
           :uber-file (str target "/uber.jar")}))


(defn make-libs-dir [basis]
  (println "build: making libs dir...")
  (->> basis
       :classpath
       (keep (fn [[lib-id {:keys [lib-name]}]]
               (when lib-name
                 lib-id)))
       (map (fn [jar-file]
              (b/copy-file {:src    jar-file
                            :target (str target
                                         "/libs/"
                                         (-> jar-file (io/file) (.getName)))})))
       (dorun)))


(defn make-cds [_basis]
  (println "build: making CDS archive...")
  (p/exec leyden-java
          (str "-XX:CacheDataStore=" target "/app.cds")
          "-XX:-ArchiveLoaderLookupCache"
          "-XX:+UseSerialGC"
          "-jar" (str target "/uber.jar")))


(defn make-graalvm-image [_basis]
  (println "build: making GrallVM native image...")
  (p/exec native-image
          "--features=clj_easy.graal_build_time.InitClojureClasses"
          "--no-fallback"
          "--install-exit-handlers"
          "--enable-monitoring"
          "--native-image-info"
          "-H:+UnlockExperimentalVMOptions"
          "-H:+PrintClassInitialization"
          "-cp" (->> (b/create-basis {:aliases [:graal]})
                     :classpath
                     (keep (fn [[jar {:keys [lib-name]}]]
                             (when lib-name
                               jar)))
                     (str/join ":"))
          "-jar" (str target "/uber.jar")
          "-o" (str target "/native-app")))


;;
;; Tools API:
;;


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn build-all [_]
  (b/delete {:path target})
  (doto (b/create-basis)
    (generate-code)
    (compile-clj)
    (make-src-jar)
    (make-aot-jar)
    (make-uber-jar)
    (make-libs-dir)
    (make-cds)
    (make-graalvm-image)))
