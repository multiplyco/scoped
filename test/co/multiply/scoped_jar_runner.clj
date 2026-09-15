(ns co.multiply.scoped-jar-runner
  "Packaged-test entry point: fail before testing if sources shadow the artifact."
  (:require [clojure.java.io :as io]
    [kaocha.runner :as kaocha])
  (:import [co.multiply.scoped MapUpdates ScopedRuntime]
    [java.io DataInputStream]
    [java.net JarURLConnection]
    [java.util.jar JarFile]))


(defn ensure!
  [condition message]
  (when-not condition (throw (ex-info message {}))))


(defn verify-artifact!
  [artifact major backend]
  (let [expected (.getCanonicalFile (io/file artifact))]
    (ensure! (= major (.major (Runtime/version))) "Test JVM version does not match the requested JDK")
    (doseq [runtime-class [ScopedRuntime MapUpdates]]
      (ensure! (= expected
                  (.getCanonicalFile (io/file (.. runtime-class getProtectionDomain getCodeSource getLocation toURI))))
        (str (.getName ^Class runtime-class) " did not load from the packaged JAR")))
    (doseq [path ["co/multiply/scoped.clj" "co/multiply/scoped/impl.cljc"
                  "co/multiply/scoped/helpers.cljc" "co/multiply/scoped/ScopedRuntime.class"
                  "co/multiply/scoped/MapUpdates.class"]]
      (let [connection (.openConnection (io/resource path))]
        (ensure! (instance? JarURLConnection connection) (str "Local source shadows JAR: " path))
        (ensure! (= expected (.getCanonicalFile (io/file (.toURI (.getJarFileURL ^JarURLConnection connection)))))
          (str "Wrong source JAR: " path))))
    ;; The carrier is selected by JDK version; both use the same Java 17 map helper.
    (doseq [[path bytecode-major] [["co/multiply/scoped/ScopedRuntime.class"
                                    (if (= backend "ScopedValueBackend") 69 61)]
                                   ["co/multiply/scoped/MapUpdates.class" 61]]]
      (with-open [input (DataInputStream. (io/input-stream (io/resource path)))]
        (.readInt input)
        (.readUnsignedShort input)
        (ensure! (= bytecode-major (.readUnsignedShort input))
          (str "Wrong selected bytecode version: " path))))
    (with-open [jar (JarFile. expected)]
      (ensure! (= ["co/multiply/scoped/MapUpdates.class"]
                  (into [] (comp (map #(.getName ^java.util.jar.JarEntry %))
                             (filter #(.endsWith ^String % "co/multiply/scoped/MapUpdates.class")))
                    (enumeration-seq (.entries jar))))
        "MapUpdates must be packaged once, as a shared base class"))
    (ensure! (= backend (ScopedRuntime/backendName)) "Wrong runtime backend")
    (println "Verified artifact origins, multi-release class selection and backend:" backend)))


(defn -main
  [artifact major backend]
  (try
    (verify-artifact! artifact (parse-long major) backend)
    (kaocha/-main)
    (finally (shutdown-agents))))
