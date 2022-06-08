(ns m1p.interpolation)

(defprotocol IInterpolate
  (interpolatable? [v]
    "Return `true` if `v` needs interpolation.")

  (interpolate [v interpolations {:keys [on-missing-key locale]}]
    "Interpolate values from the map `interpolations` into corresponding
     placeholders in `v`. `on-missing-key` is a function that can be called when
     a placeholder in `v` references a key that is not present in
     `interpolations`. `locale` is the current locale."))
