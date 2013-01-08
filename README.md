# pallet-nodelist-helpers

This library provides utility functions to more easily work with the
nodelist compute service type in Pallet.

It does this by making it possible to specify all information about a
host in a nodelist in one place, and by providing helper functions to
pass this info around to plan functions.

## Usage

The format of the hosts-config argument is very flexible and largely up
to the users of pallet-nodelist-helpers to decide. The only standardized
parts of the config is examplified below:
    {"host.to.configure" {:group-spec a-group-spec-for-host
                          :os-family :ubuntu
                          :ip 1.2.3.4
                          :admin-user {:username "root"
                                       :ssh-public-key-path  (utils/resource-path "ssh-keys/my-id_rsa.pub")
                                       :ssh-private-key-path (utils/resource-path "ssh-keys/my-id_rsa")
                                       :passphrase "foobar"}}}

(The function *utils/resource-path* is from the namespace pallet.utils and
is handy for referring to paths on the local machine)

One of the main functions provided is the ability to lift one node for one paritcular phase:
    ;; make sure we wrap the call in a with-nodelist-config
    (with-nodelist-config [hosts-config {}]
     (lift-one-node-and-phase hostname :the-phase))

*with-nodelist-config* is a utility function to bind the dynamic vars used by the helpers.

Another useful function is *ensure-nodelist-bindings* to ensure we have a correct
environment before proceeding if we assume we're withing a *with-nodelist-config*
block. Example use case from kvm-crate:
    (defn configure-kvm-server
      "Set up a machine to act as a KVM server"
      [hostname]
      (println (format "Configuring KVM server for %s.." hostname))
      (helpers/ensure-nodelist-bindings)
      (when-not (host-is-kvm-server? hostname)
        (throw (IllegalArgumentException. (format "%s is not a kvm-server!" hostname))))
      (when (fsmop/failed?
             (helpers/lift-one-node-and-phase hostname
                                              :configure-kvm-server))
        (throw (IllegalStateException. "Failed to configure KVM server!"))))

## License

Copyright © 2013 Board Intelligence

Distributed under the MIT License, see
[http://boardintelligence.mit-license.org](http://boardintelligence.mit-license.org)
for details.
