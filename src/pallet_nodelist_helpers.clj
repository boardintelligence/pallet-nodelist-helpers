(ns pallet-nodelist-helpers
  (:require
   [pallet.algo.fsmop :as fsmop]
   [pallet.configure :as configure]
   [pallet.compute :as compute]
   [pallet.api :as api]))

(def ^{:dynamic true
       :doc "Dynamic var epxected to be bound to a map of nodelist hosts config.
            The format of the map is described in the README file."}
  *nodelist-hosts-config* nil)

(def ^{:dynamic true
       :doc "Dynamic var epxected to be bound to a map to use as pallet env."}
  *pallet-environment* nil)

(def ^{:dynamic true
       :doc "Dynamic var epxected to be bound to a computeservice."}
  *compute-service* nil)

(defn- nodelist-info-for-host
  "Returns a single host info vector"
  [[hostname host-config]]
  ;; TODO: make :ubuntu configurable
  [hostname (:group-name (:group-spec host-config)) (:ip host-config) (:os-family host-config)])

(defn- generate-nodelist
  "Transform the host config hash into the format expected by pallet"
  [config]
  (vec (map nodelist-info-for-host config)))

(defn nodelist-compute-service
  "Create a nodelist compute service based on nodelist hosts config."
  [config]
  (compute/instantiate-provider "node-list" :node-list (generate-nodelist config)))

(defmacro with-nodelist-config
  "Utility function to wrap operations to use a particular nodelist config."
  [[hosts-config pallet-environment] & body]
  `(binding [*nodelist-hosts-config* ~hosts-config
             *compute-service* (nodelist-compute-service ~hosts-config)
             *pallet-environment* ~pallet-environment]
     ~@body))

(defn ensure-nodelist-bindings []
  "Ensure the relevant bindings for using nodelist helpers is in place."
  (when-not (instance? clojure.lang.IPersistentMap *nodelist-hosts-config*)
    (throw (IllegalArgumentException. "*nodelist-hosts-config* is not a map")))
  (when-not (instance? clojure.lang.IPersistentMap *pallet-environment*)
    (throw (IllegalArgumentException. "*pallet-environment* is not a map")))
  (when (nil? *compute-service*)
    (throw (IllegalArgumentException. "*compute-service* is nil"))))

(defn- get-group-spec
  "Lookup group-spec for a given hostname."
  [hostname]
  (get-in *nodelist-hosts-config* [hostname :group-spec]))

(defn- node-for-hostname
  "Looking the node in the compute service for a given hostname."
  [hostname]
  (first (filter #(= (:name %) hostname) (compute/nodes *compute-service*))))

(defn get-admin-user
  "Get the pallet admin user to use for a given hostname in the nodelist."
  [hostname & {:keys [sudo-user]}]
  (let [admin-username (get-in *nodelist-hosts-config* [hostname :admin-user :username])
        ssh-public-key-path (get-in *nodelist-hosts-config* [hostname :admin-user :ssh-public-key-path])
        ssh-private-key-path (get-in *nodelist-hosts-config* [hostname :admin-user :ssh-private-key-path])
        passphrase (.getBytes (get-in *nodelist-hosts-config* [hostname :admin-user :passphrase]))]
    (if (= admin-username "root")
         (api/make-user admin-username
                        :public-key-path ssh-public-key-path
                        :private-key-path ssh-private-key-path
                        :passphrase passphrase
                        :no-sudo true)
         (api/make-user admin-username
                        :public-key-path ssh-public-key-path
                        :private-key-path ssh-private-key-path
                        :passphrase passphrase
                        :sudo-user sudo-user))))

(defn lift-one-node-and-phase
  "Lift a given host belonging to nodelist, applying only one specified phase"
  ([hostname phase] (lift-one-node-and-phase hostname (get-admin-user hostname) phase {}))
  ([hostname phase env-options] (lift-one-node-and-phase hostname (get-admin-user hostname) phase env-options))
  ([hostname user phase env-options]
     (let [spec (get-group-spec hostname)
           node (node-for-hostname hostname)
           result (api/lift
                   {spec node}
                   :environment (merge *pallet-environment* env-options {:host-config *nodelist-hosts-config*})
                   :phase phase
                   :user user
                   :compute *compute-service*)]
       (fsmop/wait-for result)
       (when (fsmop/failed? result)
         (do
           ;; TODO: use logger here
           (println "Errors encountered:")
           (fsmop/report-operation result)))
       result)))

(defn host-has-phase?
  "Check if a given hostname has a given phase (in pallet terminology)."
  [hostname phase]
  (contains? (:phases (get-group-spec hostname)) phase))

(defn run-one-plan-fn
  "Run a single plan function for a given hostname."
  ([hostname the-plan-fn] (run-one-plan-fn hostname (get-admin-user hostname) the-plan-fn {}))
  ([hostname the-plan-fn env-options] (run-one-plan-fn hostname (get-admin-user hostname) the-plan-fn env-options))
  ([hostname user the-plan-fn env-options]
     (let [spec (api/group-spec
                 "one-off-group-spec"
                 :extends [(api/server-spec :phases {:one-off (api/plan-fn (the-plan-fn))})])
           config (get-in *nodelist-hosts-config* [hostname])
           one-off-config {hostname  (assoc config :group-spec spec)}]
       ;; nest another nodelist config with just this one and our custom spec
       (with-nodelist-config [one-off-config env-options]
         (lift-one-node-and-phase hostname user :one-off env-options)))))
