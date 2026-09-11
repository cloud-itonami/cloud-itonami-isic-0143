(ns camelops.store
  "Store abstraction for camelid-raising facility/herd records. Current
  implementation is an in-memory map; production should migrate to
  Datomic/kotoba-server (the same seam point all cloud-itonami actors
  use). Mirrors `cattleops.store` (cloud-itonami-isic-0141) in shape.

  A registered facility is the minimal unit of authority: a
  ranch/farm/herding operation must be registered before ANY proposal
  referencing it can be considered by the Governor (see
  `camelops.governor`'s `facility-registered` invariant). Facility data
  is opaque to this namespace -- callers/backends decide what a facility
  record contains (name, location, herd roster, etc); this Store only
  answers \"is this facility-id registered, and if so what's on file\".

  The append-only audit ledger (`ledger`/`append-ledger!`) is this
  actor's core missing plumbing before this fix: no audit ledger existed
  ANYWHERE in this repo (not even an aspirational stub). Every
  committed/held/approval-rejected decision fact from
  `camelops.operation`'s compiled StateGraph now lands here, so a
  facility's operating history is always a query over an immutable log
  -- the same discipline every sibling `cloud-itonami-isic-*` actor's
  ledger provides. The ledger stays append-only.")

;; Protocol for swappable store implementations
(defprotocol Store
  (registered-facility [store facility-id]
    "Retrieve a registered facility record by ID. Returns nil if the
    facility-id is nil or not registered.")
  (ledger [store]
    "The append-only audit ledger: every committed/held/approval-rejected
    decision fact, in append order.")
  (append-ledger! [store fact]
    "Append one immutable decision fact to the ledger. Returns the fact.
    Genuinely wired into `camelops.operation`'s `:commit`/`:hold` graph
    nodes -- not test-only plumbing."))

;; In-memory implementation (MemStore) for development/testing
(defrecord MemStore [facilities ledger-atom]
  Store
  (registered-facility [_store facility-id]
    (when facility-id
      (get @facilities facility-id)))
  (ledger [_store] @ledger-atom)
  (append-ledger! [_store fact]
    (swap! ledger-atom conj fact)
    fact))

(defn mem-store
  "Create an in-memory store. `initial-facilities` is an optional map of
  facility-id -> facility-record."
  [& [{:keys [initial-facilities] :or {initial-facilities {}}}]]
  (MemStore. (atom initial-facilities) (atom [])))

(defn add-facility
  "Register or update a facility in the store. Used by tests and
  simulation."
  [^MemStore store facility-id facility-data]
  (swap! (:facilities store) assoc facility-id facility-data)
  facility-data)
