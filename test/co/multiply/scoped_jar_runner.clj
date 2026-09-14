(ns co.multiply.scoped-jar-runner
  "Packaged-test entry point: fail before testing if sources shadow the artifact."
  (:require [clojure.java.io :as io]
    [kaocha.runner :as kaocha])
  (:import [co.multiply.scoped Carrier ScopedRuntime]
    [java.io DataInputStream]
    [java.net JarURLConnection]))


(defn ensure!
  [condition message]
  (when-not condition (throw (ex-info message {}))))


(defn verify-artifact!
  [artifact major backend]
  (let [expected (.getCanonicalFile (io/file artifact))]
    (ensure! (= major (.major (Runtime/version))) "Test JVM version does not match the requested JDK")
    (doseq [cls [Carrier ScopedRuntime]]
      (ensure! (= expected
                  (.getCanonicalFile (io/file (.. cls getProtectionDomain getCodeSource getLocation toURI))))
        (str "Class did not load from the packaged JAR: " cls)))
    (doseq [path ["co/multiply/scoped.clj" "co/multiply/scoped/impl.cljc"
                  "co/multiply/scoped/helpers.cljc" "co/multiply/scoped/Carrier.class"]]
      (let [connection (.openConnection (io/resource path))]
        (ensure! (instance? JarURLConnection connection) (str "Local source shadows JAR: " path))
        (ensure! (= expected (.getCanonicalFile (io/file (.toURI (.getJarFileURL ^JarURLConnection connection)))))
          (str "Wrong source JAR: " path))))
    ;; Check selected class bytes, including the base carrier selected on Java 25.
    (with-open [input (DataInputStream. (io/input-stream (io/resource "co/multiply/scoped/Carrier.class")))]
      (.readInt input)
      (.readUnsignedShort input)
      (ensure! (= (if (= backend "ScopedValueBackend") 69 61) (.readUnsignedShort input))
        "Multi-release loader selected the wrong Carrier class"))
    (ensure! (= backend (ScopedRuntime/backendName)) "Wrong runtime backend")
    (println "Verified artifact origins, multi-release class selection and backend:" backend)))


(defn -main
  [artifact major backend]
  (try
    (verify-artifact! artifact (parse-long major) backend)
    (kaocha/-main)
    (finally (shutdown-agents))))
