(print (->> "./deps.edn"
            slurp
            read-string
            :aliases
            keys
            sort
            (apply str)))
