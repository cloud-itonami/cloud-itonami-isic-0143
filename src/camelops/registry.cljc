(ns camelops.registry
  "Pure validation functions for camelid-raising operations. These are
  called by the Governor to independently verify proposal parameters --
  the LLM advisor's confidence is NOT sufficient to override these checks.
  Mirrors `cattleops.registry` (cloud-itonami-isic-0141) in shape, plus a
  fiber-yield check genuinely new to this fiber-producing (llama/alpaca)
  vertical."
  )

(defn cost-exceeds-threshold?
  "Independently verify a proposed spend against its category/default
  threshold. Inclusive at the boundary (exactly-at-threshold does not
  escalate)."
  [cost threshold]
  (> cost threshold))

(defn herd-count-non-positive?
  "A logged herd count of zero or negative is not a real observation --
  reject it as a HARD violation rather than silently accepting bad data
  into the record."
  [count]
  (<= count 0))

(defn fiber-yield-invalid?
  "A logged fiber yield (llama/alpaca shearing, kg) must never be
  negative -- zero is a valid observation (e.g. a working camel or a
  not-yet-sheared animal), but a negative weight is not a real
  observation and is rejected as a HARD violation rather than silently
  accepted into the record."
  [fiber-yield]
  (neg? fiber-yield))

(defn confidence-below-floor?
  "Independently verify a proposal's stated confidence against the
  Governor's confidence floor."
  [confidence floor]
  (< confidence floor))
